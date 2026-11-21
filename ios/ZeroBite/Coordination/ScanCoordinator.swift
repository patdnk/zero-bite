//
//  ScanCoordinator.swift
//  ZeroBite
//
//  Runs the add-food sheet's state machine (ScanFlow) and its effects: reading a photo,
//  looking up a barcode, and closing the sheet. Created fresh for each presentation.
//

import Foundation
import Observation
import UIKit

@MainActor
@Observable
final class ScanCoordinator {

    typealias ReadLabel = @Sendable (Data) async throws -> ScanResult

    private enum EffectID: Hashable {
        case readLabel
        case lookUp
    }

    let supportsLiveScanning: Bool
    private let store: FlowStore<ScanFlow>

    init(
        supportsLiveScanning: Bool,
        productLookup: any ProductLookup,
        readLabel: @escaping ReadLabel = { try await LabelScanner.scan(imageData: $0) },
        onFinished: @escaping @MainActor () -> Void
    ) {
        self.supportsLiveScanning = supportsLiveScanning
        store = FlowStore(ScanFlow.State()) { effect in
            switch effect {
            case .readLabel(let data):
                .run(id: EffectID.readLabel) {
                    guard let normalized = UIImage(data: data)?.normalizedJPEGData() else {
                        return .labelReadFailed(.notAPhoto)
                    }
                    do {
                        return .labelRead(try await readLabel(normalized))
                    } catch {
                        return .labelReadFailed(.scanner(error.localizedDescription))
                    }
                }
            case .cancelReadLabel:
                .cancel(id: EffectID.readLabel)
            case .lookUpProduct(let barcode):
                .run(id: EffectID.lookUp) {
                    .productLookedUp(try? await productLookup.lookup(barcode: barcode))
                }
            case .cancelLookUp:
                .cancel(id: EffectID.lookUp)
            case .close:
                .perform(onFinished)
            }
        }
    }

    var state: ScanFlow.State { store.state }

    @discardableResult
    func send(_ event: ScanFlow.Event) -> Task<Void, Never> {
        store.send(event)
    }
}
