//@formatter:off
/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (https://www.swig.org).
 * Version 4.2.1
 *
 * Do not make changes to this file unless you know what you are doing - modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package me.him188.ani.app.torrent.anitorrent.binding;

public class torrent_handle_t {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected torrent_handle_t(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(torrent_handle_t obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected static long swigRelease(torrent_handle_t obj) {
    long ptr = 0;
    if (obj != null) {
      if (!obj.swigCMemOwn)
        throw new RuntimeException("Cannot release ownership as memory is not owned");
      ptr = obj.swigCPtr;
      obj.swigCMemOwn = false;
      obj.delete();
    }
    return ptr;
  }

  @SuppressWarnings({"deprecation", "removal"})
  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        anitorrentJNI.delete_torrent_handle_t(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public void setId(long value) {
    anitorrentJNI.torrent_handle_t_id_set(swigCPtr, this, value);
  }

  public long getId() {
    return anitorrentJNI.torrent_handle_t_id_get(swigCPtr, this);
  }

  public torrent_info_t get_info_view() {
    long cPtr = anitorrentJNI.torrent_handle_t_get_info_view(swigCPtr, this);
    return (cPtr == 0) ? null : new torrent_info_t(cPtr, false);
  }

  public torrent_handle_t.reload_file_result_t reload_file() {
    return torrent_handle_t.reload_file_result_t.swigToEnum(anitorrentJNI.torrent_handle_t_reload_file(swigCPtr, this));
  }

  public boolean is_valid() {
    return anitorrentJNI.torrent_handle_t_is_valid(swigCPtr, this);
  }

  public int get_state() {
    return anitorrentJNI.torrent_handle_t_get_state(swigCPtr, this);
  }

  public void post_status_updates() {
    anitorrentJNI.torrent_handle_t_post_status_updates(swigCPtr, this);
  }

  public void post_save_resume() {
    anitorrentJNI.torrent_handle_t_post_save_resume(swigCPtr, this);
  }

  public void post_file_progress() {
    anitorrentJNI.torrent_handle_t_post_file_progress(swigCPtr, this);
  }

  public void set_piece_deadline(int index, int deadline) {
    anitorrentJNI.torrent_handle_t_set_piece_deadline(swigCPtr, this, index, deadline);
  }

  public void reset_piece_deadline(int index) {
    anitorrentJNI.torrent_handle_t_reset_piece_deadline(swigCPtr, this, index);
  }

  public void clear_piece_deadlines() {
    anitorrentJNI.torrent_handle_t_clear_piece_deadlines(swigCPtr, this);
  }

  public void set_peer_endgame(boolean endgame) {
    anitorrentJNI.torrent_handle_t_set_peer_endgame(swigCPtr, this, endgame);
  }

  public void add_tracker(String url, short tier, short fail_limit) {
    anitorrentJNI.torrent_handle_t_add_tracker(swigCPtr, this, url, tier, fail_limit);
  }

  public void resume() {
    anitorrentJNI.torrent_handle_t_resume(swigCPtr, this);
  }

  public void ignore_all_files() {
    anitorrentJNI.torrent_handle_t_ignore_all_files(swigCPtr, this);
  }

  public void set_file_priority(int index, short priority) {
    anitorrentJNI.torrent_handle_t_set_file_priority(swigCPtr, this, index, priority);
  }

  public String make_magnet_uri() {
    return anitorrentJNI.torrent_handle_t_make_magnet_uri(swigCPtr, this);
  }

  public torrent_handle_t() {
    this(anitorrentJNI.new_torrent_handle_t(), true);
  }

  public final static class reload_file_result_t {
    public final static torrent_handle_t.reload_file_result_t kReloadFileSuccess = new torrent_handle_t.reload_file_result_t("kReloadFileSuccess", anitorrentJNI.torrent_handle_t_kReloadFileSuccess_get());
    public final static torrent_handle_t.reload_file_result_t kReloadFileNullHandle = new torrent_handle_t.reload_file_result_t("kReloadFileNullHandle");
    public final static torrent_handle_t.reload_file_result_t kReloadFileNullFile = new torrent_handle_t.reload_file_result_t("kReloadFileNullFile");

    public final int swigValue() {
      return swigValue;
    }

    public String toString() {
      return swigName;
    }

    public static reload_file_result_t swigToEnum(int swigValue) {
      if (swigValue < swigValues.length && swigValue >= 0 && swigValues[swigValue].swigValue == swigValue)
        return swigValues[swigValue];
      for (int i = 0; i < swigValues.length; i++)
        if (swigValues[i].swigValue == swigValue)
          return swigValues[i];
      throw new IllegalArgumentException("No enum " + reload_file_result_t.class + " with value " + swigValue);
    }

    private reload_file_result_t(String swigName) {
      this.swigName = swigName;
      this.swigValue = swigNext++;
    }

    private reload_file_result_t(String swigName, int swigValue) {
      this.swigName = swigName;
      this.swigValue = swigValue;
      swigNext = swigValue+1;
    }

    private reload_file_result_t(String swigName, reload_file_result_t swigEnum) {
      this.swigName = swigName;
      this.swigValue = swigEnum.swigValue;
      swigNext = this.swigValue+1;
    }

    private static reload_file_result_t[] swigValues = { kReloadFileSuccess, kReloadFileNullHandle, kReloadFileNullFile };
    private static int swigNext = 0;
    private final int swigValue;
    private final String swigName;
  }

}

//@formatter:on