/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// macOS 系统分享菜单 (NSSharingServicePicker) 的 JNI 实现, 对应 Kotlin 的 me.him188.ani.utils.macos.share.MacosShareSheetNative.
// AppKit 只能在主线程使用, 所以把创建与显示投递到主队列.

#import <AppKit/AppKit.h>
#import <Foundation/Foundation.h>

#include <jni.h>

#include <cstdint>

namespace {

// 正在显示的 picker: 菜单显示期间要活着, 下一次分享替换, 旧的随之释放.
NSSharingServicePicker *shownPicker = nil;

NSString *toNSString(JNIEnv *env, jstring string) {
    if (string == nullptr) return nil;
    const jsize length = env->GetStringLength(string);
    const jchar *chars = env->GetStringChars(string, nullptr);
    if (chars == nullptr) return nil;
    NSString *result = [NSString stringWithCharacters:reinterpret_cast<const unichar *>(chars)
                                               length:static_cast<NSUInteger>(length)];
    env->ReleaseStringChars(string, chars);
    return result;
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_me_him188_ani_utils_macos_share_MacosShareSheetNative_showSharingServicePicker(
    JNIEnv *env, jclass, jlong window, jstring path,
    jboolean hasAnchor, jdouble left, jdouble top, jdouble width, jdouble height) {
    NSString *filePath = toNSString(env, path);
    if (filePath == nil || window == 0) return JNI_FALSE;
    id handle = (__bridge id) reinterpret_cast<void *>(static_cast<intptr_t>(window));
    const BOOL anchored = hasAnchor == JNI_TRUE;

    dispatch_async(dispatch_get_main_queue(), ^{
        NSView *view = nil;
        if ([handle isKindOfClass:[NSWindow class]]) {
            view = ((NSWindow *) handle).contentView;
        } else if ([handle isKindOfClass:[NSView class]]) {
            view = (NSView *) handle;
        }
        if (view == nil) return;

        NSRect rect;
        if (anchored) {
            // 锚点是内容区坐标 (原点左上, y 向下); 没有翻转的视图原点在左下, 用视图高度换算
            const CGFloat y = view.isFlipped ? top : NSHeight(view.bounds) - top - height;
            rect = NSMakeRect(left, y, width, height);
        } else {
            rect = view.bounds;
        }

        NSURL *url = [NSURL fileURLWithPath:filePath];
        NSSharingServicePicker *picker = [[NSSharingServicePicker alloc] initWithItems:@[ url ]];
        shownPicker = picker;
        [picker showRelativeToRect:rect ofView:view preferredEdge:NSRectEdgeMaxY];
    });
    return JNI_TRUE;
}
