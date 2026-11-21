//
//  SmokeTests.swift
//  ZeroBiteUITests
//

import XCTest

/// Launch → (welcome) → add an item by hand → find it in the fridge. Mirrors the Android SmokeTest.
final class SmokeTests: XCTestCase {

    override func setUp() {
        continueAfterFailure = false
    }

    @MainActor
    func testAddAnItemByHand() {
        let app = XCUIApplication()
        // English copy, whatever the simulator's language: the test taps "Add manually" by its label.
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_GB"]
        app.launch()

        let skip = app.buttons["welcome.skip"]
        let add = app.buttons["inventory.addButton"]
        XCTAssertTrue(skip.waitForExistence(timeout: 15) || add.exists, "Neither the welcome nor the inventory screen appeared")

        if skip.exists {
            skip.tap()
            let dontShare = app.buttons["welcome.dontShare"]
            XCTAssertTrue(dontShare.waitForExistence(timeout: 5))
            dontShare.tap()
            let notNow = app.buttons["welcome.notNow"]
            XCTAssertTrue(notNow.waitForExistence(timeout: 5))
            notNow.tap()
        }

        XCTAssertTrue(add.waitForExistence(timeout: 5))
        add.tap()
        let manual = app.buttons["Add manually"]
        XCTAssertTrue(manual.waitForExistence(timeout: 5))
        manual.tap()

        let name = "Smoke test \(Int(Date().timeIntervalSince1970) % 100_000)"
        let nameField = app.textFields["itemForm.name"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 5))
        nameField.tap()
        nameField.typeText(name)
        app.buttons["itemForm.save"].tap()

        let search = app.textFields["inventory.search"]
        XCTAssertTrue(search.waitForExistence(timeout: 5))
        search.tap()
        search.typeText(name)
        // The fridge card merges its texts into one element ("<icon>, <name>, 1 pc, …"), so match by containment.
        let card = app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", name)).firstMatch
        XCTAssertTrue(card.waitForExistence(timeout: 5), "The new item isn't in the inventory")
    }
}
