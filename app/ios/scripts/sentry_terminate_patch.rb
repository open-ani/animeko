# Sentry's fatal-event cleanup disables the C++ monitor and clears its saved handler.
# Keep the predecessor alive for this invocation so Kotlin/Native can dispatch its
# unhandled-exception hook after Sentry has recorded the native crash.
module SentryTerminatePatch
  SOURCE = 'Sentry/Sources/SentryCrash/Recording/Monitors/SentryCrashMonitor_CPPException.cpp'.freeze
  HEADER = "static void\nCPPExceptionTerminate(void)\n{".freeze
  CALL = '    sentrycrashcm_cppexception_callOriginalTerminationHandler();'.freeze
  SNAPSHOT = <<-CPP.chomp.freeze

    // Animeko: fatal-event cleanup may clear the monitor's saved handler.
    const auto aniOriginalTerminateHandler = g_originalTerminateHandler;
  CPP
  DELEGATE = <<-CPP.chomp.freeze
    if (aniOriginalTerminateHandler != nullptr) {
        aniOriginalTerminateHandler();
    }
  CPP

  def self.patch_source(source)
    functions = source.scan(/static void\nCPPExceptionTerminate\(void\)\n\{.*?^\}/m)
    raise 'Unsupported Sentry C++ monitor: review the terminate-handler patch' unless functions.size == 1

    function = functions.first
    if function.include?('aniOriginalTerminateHandler')
      unless function.include?(SNAPSHOT) && function.include?(DELEGATE) && !function.include?(CALL)
        raise 'Incomplete Sentry terminate-handler patch'
      end
      return source
    end
    unless function.scan(CALL).size == 1
      raise 'Unsupported Sentry termination delegation: review the terminate-handler patch'
    end

    patched = function.sub(HEADER, HEADER + "\n" + SNAPSHOT).sub(CALL, DELEGATE)
    source.sub(function, patched)
  end

  def self.apply(pods_root)
    path = File.join(pods_root, SOURCE)
    source = File.read(path)
    patched = patch_source(source)
    return if patched == source

    # Downloaded CocoaPods sources can be read-only.
    mode = File.stat(path).mode
    begin
      File.chmod(mode | 0o200, path)
      File.write(path, patched)
    ensure
      File.chmod(mode, path)
    end
  end
end
