// IDisposableHandle.aidl
package me.him188.ani.app.domain.torrent;

// Declare any non-default types here with import statements

/**
 * Dispose when client requires to release this binder.
 */
interface IDisposableHandle {
    void dispose();
}