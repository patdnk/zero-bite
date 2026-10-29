#!/usr/bin/env python3
"""Checks the shared Polish assets against the English source data.

Run from the repository root:  python3 scripts/check-polish-assets.py
Exits non-zero when anything is wrong. Standard library only.

What it checks
- every JSON file involved parses;
- shared-assets/recipes.pl.json covers every recipe id, every ingredient name (exactly) and the
  same number of steps as shared-assets/recipes.json, with non-empty Polish text;
- shared-assets/recipe-tags.json names every tag in both languages, and the English names equal
  what the apps show today (the raw tag, unmodified);
- shared-assets/ingredient-aliases.pl.json has a key for every non-staple ingredient (singular,
  normalised form), every key is an ingredient or a synonym-group term, aliases are unique and
  long enough to match, and every alias with "ł" also lists its "l" spelling (iOS folding keeps
  "ł", Android folds it, so this keeps both platforms equal for people typing without Polish
  characters);
- no alias makes a different ingredient match (apart from known, intended overlaps);
- testdata/recipe-matching-pl.json holds under both platforms' normalisation, using a port of the
  Swift reference matcher plus the alias rule from the plan; the English fixture is re-run as a
  sanity check of the port;
- testdata/notification-plans-pl.json uses the exact Polish notification copy from the plan.

Staples and synonym groups are read from the Swift reference implementation so they never drift.
"""

import json
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MATCHER_SWIFT = ROOT / "ios/ZeroBite/Services/Recipes/RecipeMatcher.swift"
RECIPES = ROOT / "shared-assets/recipes.json"
RECIPES_PL = ROOT / "shared-assets/recipes.pl.json"
TAGS = ROOT / "shared-assets/recipe-tags.json"
ALIASES = ROOT / "shared-assets/ingredient-aliases.pl.json"
MATCHING_EN = ROOT / "testdata/recipe-matching.json"
MATCHING_PL = ROOT / "testdata/recipe-matching-pl.json"
NOTIFICATIONS_EN = ROOT / "testdata/notification-plans.json"
NOTIFICATIONS_PL = ROOT / "testdata/notification-plans-pl.json"

# Polish notification copy, verbatim from specs/plans/2026-10-29-polish-localization.md.
PL_COPY = {
    "soon_one": "{name}: termin mija jutro",
    "soon_other": "{name}: termin mija za {n} dni",
    "soon_body": "Zaplanuj z tym posiłek – Zero Bite podpowie przepisy.",
    "today": "{name}: termin mija dziś",
    "today_body": "Zużyj to dziś albo zamroź.",
    "expired": "{name}: termin minął",
    "expired_body": "Sprawdź produkt i zaktualizuj lodówkę.",
}

# Alias overlaps that are intended. (alias owner, ingredient that also matches it).
# Polish says "makaron" for both pasta and noodles, so "makaron ryżowy" counts as pasta too, and
# "zielona cebulka" (spring onion) contains "cebulka" (onion) just as "spring onion" contains "onion".
ALLOWED_OVERLAPS = {
    ("noodle", "pasta"),
    ("spring onion", "onion"),
}

problems = []
notes = []


def fail(message):
    problems.append(message)


def load(path):
    try:
        with open(path, encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, json.JSONDecodeError) as error:
        fail(f"{path.relative_to(ROOT)}: does not parse ({error})")
        return None


# ---------------------------------------------------------------------------
# Port of the matcher's normalisation (RecipeMatcher.swift / RecipeMatcher.kt)
# ---------------------------------------------------------------------------

# Android's TextFolding table folds these letters too; Foundation's diacritic folding does not,
# because they have no Unicode decomposition. Only "ł" matters for Polish.
ANDROID_EXTRA_FOLDS = str.maketrans({"ł": "l", "Ł": "L", "đ": "d", "Đ": "D", "ø": "o", "Ø": "O",
                                     "ı": "i", "ŀ": "l", "Ŀ": "L", "ŧ": "t", "Ŧ": "T"})


def fold(text, platform):
    if platform == "android":
        text = text.translate(ANDROID_EXTRA_FOLDS)
    decomposed = unicodedata.normalize("NFD", text)
    stripped = "".join(c for c in decomposed if not unicodedata.combining(c))
    return unicodedata.normalize("NFC", stripped).lower()


def singularize(word):
    if len(word) <= 3:
        return word
    if word.endswith("ies"):
        return word[:-3] + "y"
    if word.endswith("oes"):
        return word[:-2]
    if word.endswith(("ss", "us", "is")):
        return word
    if word.endswith(("ches", "shes", "xes", "ses")):
        return word[:-2]
    if word.endswith("s"):
        return word[:-1]
    return word


def normalize(text, platform="ios"):
    folded = fold(text, platform)
    cleaned = "".join(c if (c.isalpha() or c == " ") else " " for c in folded)
    return " ".join(singularize(w) for w in cleaned.split())


def contains_words(needle, haystack):
    if len(needle) < 3:
        return False
    return f" {needle} " in f" {haystack} "


def swift_string_arrays(source, declaration):
    """Returns the string literals of each innermost [...] after `declaration` up to `.map`."""
    start = source.find(declaration)
    if start < 0:
        raise ValueError(f"could not find {declaration!r} in {MATCHER_SWIFT.name}")
    end = source.find("].map", start)
    body = source[start + len(declaration):end + 1]
    rows = re.findall(r"\[([^\[\]]*)\]", body)
    return [re.findall(r'"([^"]*)"', row) for row in rows if '"' in row]


class Matcher:
    def __init__(self, staples, groups, aliases, platform):
        self.platform = platform
        self.staples = {self.norm(s) for s in staples}
        self.groups = [[self.norm(t) for t in g] for g in groups]
        self.aliases = {}
        for key, values in aliases.items():
            self.aliases.setdefault(self.norm(key), []).extend(self.norm(v) for v in values)

    def norm(self, text):
        return normalize(text, self.platform)

    def group_index(self, name):
        for index, group in enumerate(self.groups):
            if any(term == name or contains_words(term, name) for term in group):
                return index
        return None

    def is_staple(self, name):
        n = self.norm(name)
        return n in self.staples or any(contains_words(s, n) for s in self.staples)

    def alias_terms(self, normalized_ingredient):
        terms = [normalized_ingredient]
        index = self.group_index(normalized_ingredient)
        if index is not None:
            terms += self.groups[index]
        return terms

    def matches(self, ingredient, item):
        a, b = self.norm(ingredient), self.norm(item)
        if not a or not b:
            return False
        if a == b or contains_words(a, b) or contains_words(b, a):
            return True
        ga, gb = self.group_index(a), self.group_index(b)
        if ga is not None and ga == gb:
            return True
        # Plan, "Matching semantics": Polish aliases of the ingredient or of its synonym group.
        for term in self.alias_terms(a):
            if any(contains_words(alias, b) for alias in self.aliases.get(term, [])):
                return True
        return False


# ---------------------------------------------------------------------------
# Checks
# ---------------------------------------------------------------------------

def check_recipes(recipes, overlay):
    by_id = {r["id"]: r for r in recipes}
    missing = sorted(set(by_id) - set(overlay))
    extra = sorted(set(overlay) - set(by_id))
    if missing:
        fail(f"recipes.pl.json: missing recipes {missing}")
    if extra:
        fail(f"recipes.pl.json: unknown recipe ids {extra}")
    english_words = re.compile(r"\b(tbsp|tsp|pinch|splash|handful|cloves?|to serve|slices?)\b", re.I)
    for rid, recipe in by_id.items():
        pl = overlay.get(rid)
        if pl is None:
            continue
        where = f"recipes.pl.json[{rid}]"
        for field in ("title", "summary"):
            if not isinstance(pl.get(field), str) or not pl[field].strip():
                fail(f"{where}: empty or missing {field}")
        steps = pl.get("steps")
        if not isinstance(steps, list) or len(steps) != len(recipe["steps"]):
            fail(f"{where}: {len(steps) if isinstance(steps, list) else 'no'} steps, "
                 f"English has {len(recipe['steps'])}")
        elif any(not isinstance(s, str) or not s.strip() for s in steps):
            fail(f"{where}: empty step")
        names = [i["name"] for i in recipe["ingredients"]]
        ingredients = pl.get("ingredients") or {}
        if sorted(ingredients) != sorted(names):
            fail(f"{where}: ingredient keys {sorted(ingredients)} != English names {sorted(names)}")
        for name, entry in ingredients.items():
            for field in ("name", "amount"):
                if not isinstance(entry.get(field), str) or not entry[field].strip():
                    fail(f"{where}.ingredients[{name}]: empty or missing {field}")
        texts = [pl.get("title", ""), pl.get("summary", "")] + list(steps or []) + \
            [v for e in ingredients.values() for v in (e.get("name", ""), e.get("amount", ""))]
        for text in texts:
            if english_words.search(text):
                fail(f"{where}: untranslated English in {text!r}")
            if '"' in text or " - " in text:
                fail(f"{where}: use Polish typography (no straight quotes or spaced hyphens) in {text!r}")
            if re.search(r"\d\.\d", text):
                fail(f"{where}: use a decimal comma in {text!r}")


def check_tags(recipes, tags):
    used = sorted({t for r in recipes for t in r["tags"]})
    for language in ("en", "pl"):
        names = tags.get(language, {})
        if sorted(names) != used:
            fail(f"recipe-tags.json[{language}]: keys {sorted(names)} != tags in use {used}")
        for tag, label in names.items():
            if not isinstance(label, str) or not label.strip():
                fail(f"recipe-tags.json[{language}][{tag}]: empty")
    # iOS (Tag(text:)) and Android (Tag(it)) render the raw tag today.
    for tag, label in tags.get("en", {}).items():
        if label != tag:
            fail(f"recipe-tags.json[en][{tag}]: {label!r} differs from what the apps show today ({tag!r})")


def check_aliases(recipes, aliases, matcher):
    norm = matcher.norm
    ingredient_names = sorted({i["name"] for r in recipes for i in r["ingredients"]})
    non_staples = sorted({norm(n) for n in ingredient_names if not matcher.is_staple(n)})
    group_terms = {t for g in matcher.groups for t in g}
    keys = {}
    for key in aliases:
        if norm(key) in keys:
            fail(f"ingredient-aliases.pl.json: keys {keys[norm(key)]!r} and {key!r} normalise the same")
        keys[norm(key)] = key
        if norm(key) != key:
            fail(f"ingredient-aliases.pl.json: key {key!r} is not in singular, normalised form ({norm(key)!r})")
        if norm(key) not in non_staples and norm(key) not in group_terms:
            fail(f"ingredient-aliases.pl.json: key {key!r} is neither a recipe ingredient nor a synonym-group term")
    for name in non_staples:
        if name not in keys:
            fail(f"ingredient-aliases.pl.json: no aliases for non-staple ingredient {name!r}")

    owner = {}
    for key, values in aliases.items():
        if not isinstance(values, list) or not values:
            fail(f"ingredient-aliases.pl.json[{key}]: needs a non-empty list")
            continue
        seen = set()
        for alias in values:
            n = norm(alias)
            if len(n) < 3:
                fail(f"ingredient-aliases.pl.json[{key}]: {alias!r} is too short to ever match")
            if n == norm(key):
                fail(f"ingredient-aliases.pl.json[{key}]: {alias!r} repeats the English term")
            if n in seen:
                fail(f"ingredient-aliases.pl.json[{key}]: {alias!r} is listed twice (after normalisation)")
            seen.add(n)
            if n in owner and owner[n] != key:
                fail(f"ingredient-aliases.pl.json: {alias!r} is listed under both {owner[n]!r} and {key!r}")
            owner[n] = key
            if "ł" in alias.lower():
                plain = alias.replace("ł", "l").replace("Ł", "L")
                if norm(plain) not in {norm(v) for v in values}:
                    fail(f"ingredient-aliases.pl.json[{key}]: {alias!r} needs its plain spelling {plain!r} "
                         f"too (iOS does not fold ł)")
    return non_staples


def check_overlaps(aliases, non_staples, matcher):
    """An alias written as an item name must not satisfy an unrelated ingredient."""
    vocabulary = sorted(set(non_staples) | {t for g in matcher.groups for t in g})

    def cluster(term):
        index = matcher.group_index(matcher.norm(term))
        return ("group", index) if index is not None else ("term", term)

    def label(term):
        index = matcher.group_index(matcher.norm(term))
        return matcher.groups[index][0] if index is not None else term

    overlaps = set()
    for key, values in aliases.items():
        for alias in values:
            for term in vocabulary:
                if cluster(term) == cluster(key):
                    continue
                if matcher.matches(term, alias):
                    pair = (label(key), label(term))
                    if pair in ALLOWED_OVERLAPS:
                        overlaps.add(pair)
                    else:
                        fail(f"alias {alias!r} ({key}) also matches unrelated ingredient {term!r} "
                             f"on {matcher.platform}")
    return overlaps


def check_matching_fixture(fixture, matchers):
    cases = fixture.get("nameMatches", [])
    negatives = [c for c in cases if c["matches"] is False]
    if len(cases) < 20:
        fail(f"recipe-matching-pl.json: {len(cases)} cases, need at least 20")
    if len(negatives) < 5:
        fail(f"recipe-matching-pl.json: {len(negatives)} negatives, need at least 5")
    required = [("eggs", "Jajka", True), ("eggs", "Bakłażan", False)]
    for ingredient, item, expected in required:
        if not any(c["ingredient"] == ingredient and c["item"] == item and c["matches"] is expected
                   for c in cases):
            fail(f"recipe-matching-pl.json: required case {ingredient!r} / {item!r} -> {expected} is missing")
    for matcher in matchers:
        for case in cases:
            got = matcher.matches(case["ingredient"], case["item"])
            if got != case["matches"]:
                fail(f"recipe-matching-pl.json on {matcher.platform}: {case['ingredient']!r} vs "
                     f"{case['item']!r} gives {got}, expected {case['matches']}")
    return len(cases), len(negatives)


def check_english_port(fixture, matchers):
    for matcher in matchers:
        for case in fixture.get("nameMatches", []):
            if matcher.matches(case["ingredient"], case["item"]) != case["matches"]:
                fail(f"matcher port disagrees with recipe-matching.json on {matcher.platform}: {case}")
        for case in fixture.get("staples", []):
            if matcher.is_staple(case["name"]) != case["isStaple"]:
                fail(f"matcher port disagrees with recipe-matching.json staples on {matcher.platform}: {case}")


def check_notifications(english, polish):
    for field in ("timeZone", "now"):
        if polish.get(field) != english.get(field):
            fail(f"notification-plans-pl.json: {field} {polish.get(field)!r} differs from the English fixture")
    lead_days = set()
    suffixes = set()
    for case in polish.get("cases", []):
        prefs = case["preferences"]
        names = {item["id"]: item["name"] for item in case["items"]}
        if prefs["enabled"]:
            lead_days.add(prefs["leadDays"])
        for planned in case["expected"]:
            name = names[planned["itemId"]]
            suffix = planned["suffix"]
            suffixes.add(suffix)
            if suffix == "soon":
                n = prefs["leadDays"]
                title = (PL_COPY["soon_one"] if n == 1 else PL_COPY["soon_other"]).format(name=name, n=n)
                body = PL_COPY["soon_body"]
            else:
                title = PL_COPY[suffix].format(name=name)
                body = PL_COPY[f"{suffix}_body"]
            if planned["title"] != title:
                fail(f"notification-plans-pl.json[{case['name']}]: title {planned['title']!r} != {title!r}")
            if planned["body"] != body:
                fail(f"notification-plans-pl.json[{case['name']}]: body {planned['body']!r} != {body!r}")
    if not {1, 5} <= lead_days:
        fail(f"notification-plans-pl.json: needs leadDays 1 and 5 cases (has {sorted(lead_days)})")
    if not {"soon", "today", "expired"} <= suffixes:
        fail(f"notification-plans-pl.json: needs soon, today and expired reminders (has {sorted(suffixes)})")
    return len(polish.get("cases", [])), sorted(lead_days)


def main():
    data = {path: load(path) for path in (RECIPES, RECIPES_PL, TAGS, ALIASES, MATCHING_EN, MATCHING_PL,
                                          NOTIFICATIONS_EN, NOTIFICATIONS_PL)}
    if problems:
        return report()
    source = MATCHER_SWIFT.read_text(encoding="utf-8")
    staples = [s for row in swift_string_arrays(source, "static let staples: Set<String> = Set(") for s in row]
    groups = swift_string_arrays(source, "static let synonymGroups: [[String]] = [")
    if not staples or len(groups) < 10:
        fail("could not read staples / synonym groups from RecipeMatcher.swift")
        return report()

    recipes = data[RECIPES]
    aliases = data[ALIASES]
    matchers = [Matcher(staples, groups, aliases, platform) for platform in ("ios", "android")]
    check_english_port(data[MATCHING_EN], [Matcher(staples, groups, {}, p) for p in ("ios", "android")])

    check_recipes(recipes, data[RECIPES_PL])
    check_tags(recipes, data[TAGS])
    non_staples = check_aliases(recipes, aliases, matchers[0])
    overlaps = set()
    for matcher in matchers:
        overlaps |= check_overlaps(aliases, non_staples, matcher)
    cases, negatives = check_matching_fixture(data[MATCHING_PL], matchers)
    notification_cases, lead_days = check_notifications(data[NOTIFICATIONS_EN], data[NOTIFICATIONS_PL])

    alias_count = sum(len(v) for v in aliases.values())
    notes.append(f"recipes: {len(recipes)} English, {len(data[RECIPES_PL])} Polish overlays "
                 f"({sum(len(r['ingredients']) for r in recipes)} ingredients, "
                 f"{sum(len(r['steps']) for r in recipes)} steps)")
    notes.append(f"tags: {len(data[TAGS]['pl'])} in en + pl")
    notes.append(f"aliases: {len(aliases)} keys, {alias_count} Polish forms; "
                 f"{len(non_staples)} non-staple ingredients covered")
    notes.append(f"intended overlaps seen: {sorted(overlaps) or 'none'}")
    notes.append(f"recipe-matching-pl.json: {cases} cases ({negatives} negative) pass on ios and android folding")
    notes.append(f"notification-plans-pl.json: {notification_cases} cases, leadDays {lead_days}, copy verbatim")
    return report()


def report():
    for note in notes:
        print(f"ok  {note}")
    for problem in problems:
        print(f"ERR {problem}")
    if problems:
        print(f"FAILED: {len(problems)} problem(s)")
        return 1
    print("All Polish assets OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
