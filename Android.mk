#
# Copyright (C) 2014 MediaTek Inc.
# Modification based on code covered by the mentioned copyright
# and/or permission notice(s).
#
# Copyright 2007-2008 The Android Open Source Project 

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
# Include res dir from chips
chips_dir := ../../../frameworks/ex/chips/res
res_dirs := $(chips_dir) res

$(shell rm -f $(LOCAL_PATH)/chips)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := Mms

# Builds against the public SDK
#LOCAL_SDK_VERSION := current

LOCAL_JAVA_LIBRARIES += mediatek-framework
LOCAL_JAVA_LIBRARIES += telephony-common
LOCAL_STATIC_JAVA_LIBRARIES += android-common jsr305
LOCAL_STATIC_JAVA_LIBRARIES += libchips
LOCAL_STATIC_JAVA_LIBRARIES += android-common-chips
LOCAL_STATIC_JAVA_LIBRARIES += com.android.vcard
LOCAL_STATIC_JAVA_LIBRARIES += com.mediatek.mms.ext

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))
LOCAL_AAPT_FLAGS := --auto-add-overlay
LOCAL_AAPT_FLAGS += --extra-packages com.android.mtkex.chips

LOCAL_REQUIRED_MODULES := SoundRecorder
#LOCAL_PROGUARD_ENABLED := disabled
LOCAL_PROGUARD_FLAG_FILES := proguard.flags

LOCAL_PRIVILEGED_MODULE := true

include $(BUILD_PACKAGE)

# This finds and builds the test apk as well, so a single make does both.
include $(call all-makefiles-under,$(LOCAL_PATH))
