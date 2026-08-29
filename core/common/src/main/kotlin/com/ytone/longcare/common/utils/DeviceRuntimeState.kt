package com.ytone.longcare.common.utils

/** New-version-only device state. The legacy cutover never deletes this file. */
object DeviceRuntimeState {
    const val PREFERENCES_NAME = "longcare_device_state_v1"
    const val CUTOVER_MARKER_KEY = "user_storage_namespace_cutover_v1"
    const val PRIVACY_CONSENT_KEY = "privacy_consented_v1"
    const val APP_INSTANCE_GUID_KEY = "app_instance_guid_v1"
}
