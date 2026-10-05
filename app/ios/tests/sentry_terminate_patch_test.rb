require 'minitest/autorun'
require 'open3'
require 'tmpdir'
require_relative '../scripts/sentry_terminate_patch'

class SentryTerminatePatchTest < Minitest::Test
  # Models the fatal-event cleanup performed by SentryCrashMonitor.c. The native
  # callback must survive that cleanup, even though the monitor's global is reset.
  SOURCE = <<~CPP.freeze
    static void (*g_originalTerminateHandler)() = nullptr;
    static int calls = 0;
    static void kotlinHandler() { ++calls; }
    static void sentrycrashcm_handleException(void*) { g_originalTerminateHandler = nullptr; }
    static void sentrycrashcm_cppexception_callOriginalTerminationHandler(void) {
        if (g_originalTerminateHandler) g_originalTerminateHandler();
    }
    static void
    CPPExceptionTerminate(void)
    {
        sentrycrashcm_handleException(nullptr);
        sentrycrashcm_cppexception_callOriginalTerminationHandler();
    }
    int main() {
        g_originalTerminateHandler = kotlinHandler;
        CPPExceptionTerminate();
        return calls == 1 ? 0 : 1;
    }
  CPP

  def test_delegates_after_fatal_cleanup_clears_the_predecessor
    Dir.mktmpdir do |dir|
      assert_equal 1, compile_and_run(SOURCE, dir), 'unpatched monitor must reproduce the lost callback'
      assert_equal 0, compile_and_run(SentryTerminatePatch.patch_source(SOURCE), dir)
    end
  end

  def test_repeated_pod_install_is_idempotent
    patched = SentryTerminatePatch.patch_source(SOURCE)
    assert_equal patched, SentryTerminatePatch.patch_source(patched)
  end

  def test_changed_upstream_function_fails_instead_of_silently_skipping_the_patch
    assert_raises(RuntimeError) { SentryTerminatePatch.patch_source(SOURCE.sub('CPPExceptionTerminate', 'Other')) }
    assert_raises(RuntimeError) { SentryTerminatePatch.patch_source(SOURCE.gsub(SentryTerminatePatch::CALL, '')) }
    patched = SentryTerminatePatch.patch_source(SOURCE)
    assert_raises(RuntimeError) { SentryTerminatePatch.patch_source(patched.sub(SentryTerminatePatch::DELEGATE, '')) }
  end

  private

  def compile_and_run(source, dir)
    file = File.join(dir, 'monitor.cpp')
    binary = File.join(dir, 'monitor')
    File.write(file, source)
    output, status = Open3.capture2e('xcrun', 'clang++', '-std=c++17', file, '-o', binary)
    assert status.success?, output
    _output, result = Open3.capture2e(binary)
    result.exitstatus
  end
end
