import SwiftUI
import UIKit // Import UIKit for UIViewController specifics
import application // Import your KMP shared module (ensure this is the correct name)

struct ComposeView: UIViewControllerRepresentable {
	// Use the specific type of your container view controller
	typealias UIViewControllerType = MyUIViewController
	typealias Context = UIViewControllerRepresentableContext<Self>
	
	let app: AniIosApplication
	
	func makeUIViewController(context: Context) -> UIViewControllerType {
		let containerController = MyUIViewController()

		// --- Embed KMP View Controller ---
		let kmpViewController = AniIosKt.MainViewController(
			app: app
		) // Create the KMP UIViewController instance

		// 1. Add the KMP VC as a child of the container VC
		containerController.addChild(kmpViewController)

		// 2. Add the KMP VC's view as a subview to the container VC's view
		containerController.view.addSubview(kmpViewController.view)

		// 3. Set up layout constraints to make the KMP VC's view fill the container
		kmpViewController.view.translatesAutoresizingMaskIntoConstraints = false
		NSLayoutConstraint.activate([
			kmpViewController.view.leadingAnchor.constraint(equalTo: containerController.view.leadingAnchor),
			kmpViewController.view.trailingAnchor.constraint(equalTo: containerController.view.trailingAnchor),
			kmpViewController.view.topAnchor.constraint(equalTo: containerController.view.topAnchor),
			kmpViewController.view.bottomAnchor.constraint(equalTo: containerController.view.bottomAnchor)
		])


		// 4. Notify the child view controller that it has been moved to a parent
		kmpViewController.didMove(toParent: containerController)
		// --- End Embed KMP View Controller ---

		if #available(iOS 16.0, *) {
			kmpViewController.setNeedsUpdateOfSupportedInterfaceOrientations()
		} else {
			UIViewController.attemptRotationToDeviceOrientation()
		}

		return containerController
	}

	func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
		// This method is used to pass data updates from SwiftUI to the UIViewController.
		// Not needed for the current fullscreen/system bar problem.
	}
}

class MyUIViewController : UIViewController {
	override var preferredStatusBarUpdateAnimation: UIStatusBarAnimation {
		return .slide
	}
	
	override var prefersHomeIndicatorAutoHidden: Bool {
		return MainViewControllerPropertyProvider.shared.prefersHomeIndicatorAutoHidden
	}
}

struct ContentView: View {
	let app: AniIosApplication
	
	var body: some View {
		ComposeView(app: app)
			.ignoresSafeArea(.all)
			// .ignoresSafeArea(.keyboard)
	}
}

class SwiftBridgeImpl : ISwiftBridge {
	func navigationController(_ receiver: UIViewController) -> UINavigationController? {
		return receiver.navigationController
	}
}
