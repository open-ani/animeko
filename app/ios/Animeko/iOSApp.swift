import SwiftUI
import UIKit
import application


@main
struct iOSApp: App {
	let app: AniIosApplication

	init() {
		SwiftBridgeKt.SwiftBridge = SwiftBridgeImpl()
		app = AniIosKt.startIosApp()
	}

	var body: some Scene {
		WindowGroup {
			ContentView(app: app)
		}
	}
}
