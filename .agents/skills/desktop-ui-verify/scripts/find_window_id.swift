// Prints the CGWindowID of the frontmost standard window matching the given owner.
// Usage: swift find_window_id.swift <ownerName>        (matches kCGWindowOwnerName)
//        swift find_window_id.swift --pid <pid>        (matches kCGWindowOwnerPID; most robust)
import CoreGraphics
import Foundation

let args = CommandLine.arguments
guard args.count > 1 else {
    FileHandle.standardError.write("usage: find_window_id.swift <ownerName> | --pid <pid>\n".data(using: .utf8)!)
    exit(2)
}
var byPid: Int? = nil
var byName: String? = nil
if args[1] == "--pid", args.count > 2, let p = Int(args[2]) {
    byPid = p
} else {
    byName = args[1]
}

let info = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID) as! [[String: Any]]
for w in info {
    guard let layer = w[kCGWindowLayer as String] as? Int, layer == 0,
          let num = w[kCGWindowNumber as String] as? Int else { continue }
    let owner = w[kCGWindowOwnerName as String] as? String ?? ""
    let pid = w[kCGWindowOwnerPID as String] as? Int ?? -1
    if let p = byPid, pid == p {
        print(num)
        exit(0)
    }
    if let n = byName, owner == n {
        print(num)
        exit(0)
    }
}
FileHandle.standardError.write("no on-screen window found\n".data(using: .utf8)!)
exit(1)
