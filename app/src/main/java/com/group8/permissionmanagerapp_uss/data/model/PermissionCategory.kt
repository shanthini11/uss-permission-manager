package com.group8.permissionmanagerapp_uss.data.model

/**
 * The permission categories the Permission Manager shows, with plain-language explanations.
 *
 * Research mapping:
 *  - R4  why apps need it            -> [whyAppsNeedIt]
 *  - R5  what data it gives access to -> [whatItAccesses]
 *  - R7  what happens if you deny     -> [ifYouDeny]
 *  - SR5 privacy risk                 -> [privacyRisk]
 *  - R13 clear language, R15 categories, SR2 sensitivity tags
 *
 * [androidPermissions] lists the platform permission strings that belong to the category.
 * [backgroundPermission] is the permission that turns "while in use" into "always".
 */
enum class PermissionCategory(
    val title: String,
    val sensitivity: Sensitivity,
    val shortDescription: String,
    val whatItAccesses: String,
    val whyAppsNeedIt: String,
    val ifYouDeny: String,
    val privacyRisk: String,
    val androidPermissions: List<String>,
    val backgroundPermission: String? = null,
    /** Weight used by the risk score; higher means more privacy-sensitive. */
    val riskWeight: Int,
) {
    LOCATION(
        title = "Location",
        sensitivity = Sensitivity.CRITICAL,
        shortDescription = "Where your phone is",
        whatItAccesses = "Your phone's position from GPS, Wi-Fi and mobile networks. " +
            "Precise location can pinpoint the building you are in; approximate location " +
            "shows roughly which neighbourhood you are in.",
        whyAppsNeedIt = "Maps and navigation, ride and food delivery, weather for your area, " +
            "tagging photos or posts with a place, and finding nearby shops or friends.",
        ifYouDeny = "Features that depend on where you are stop working or ask you to type an " +
            "address instead. The rest of the app usually keeps working.",
        privacyRisk = "Location over time reveals your home, workplace, routine and the places " +
            "you visit. 'Always allowed' lets an app track you even when you are not using it.",
        androidPermissions = listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
        ),
        backgroundPermission = "android.permission.ACCESS_BACKGROUND_LOCATION",
        riskWeight = 4,
    ),
    CAMERA(
        title = "Camera",
        sensitivity = Sensitivity.CRITICAL,
        shortDescription = "Take photos and record video",
        whatItAccesses = "Live images from your front and back cameras, including taking photos " +
            "and recording video.",
        whyAppsNeedIt = "Taking photos and videos, video calls, scanning QR codes or documents, " +
            "and face-based features like filters.",
        ifYouDeny = "You can't take photos, scan codes or join video calls inside the app. " +
            "You can usually still upload photos you already have.",
        privacyRisk = "The camera can capture you, your surroundings and other people. Android " +
            "shows a green dot in the status bar whenever an app uses it.",
        androidPermissions = listOf("android.permission.CAMERA"),
        riskWeight = 4,
    ),
    MICROPHONE(
        title = "Microphone",
        sensitivity = Sensitivity.CRITICAL,
        shortDescription = "Record audio",
        whatItAccesses = "Sound around your phone, including your voice and conversations nearby.",
        whyAppsNeedIt = "Voice and video calls, voice messages, voice search and assistants, " +
            "and recording videos with sound.",
        ifYouDeny = "Calls, voice notes and voice search stop working. Videos you record in the " +
            "app may have no sound.",
        privacyRisk = "Audio can capture private conversations. Android shows a green dot in the " +
            "status bar whenever an app is listening.",
        androidPermissions = listOf("android.permission.RECORD_AUDIO"),
        riskWeight = 4,
    ),
    FILES_PHOTOS(
        title = "Photos & Files",
        sensitivity = Sensitivity.SENSITIVE,
        shortDescription = "Your photos, videos, music and files",
        whatItAccesses = "Photos, videos, audio and documents stored on your phone. Photos can " +
            "include hidden details like the time and place they were taken.",
        whyAppsNeedIt = "Sharing or editing photos, attaching files to messages, playing your " +
            "music, and saving downloads.",
        ifYouDeny = "You can't pick files from inside the app. Most apps still let you share " +
            "single items using the system picker.",
        privacyRisk = "Full access lets an app read every photo and document, not just the ones " +
            "you choose. 'Selected photos only' is the safer option.",
        androidPermissions = listOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        ),
        riskWeight = 3,
    ),
    CONTACTS(
        title = "Contacts",
        sensitivity = Sensitivity.SENSITIVE,
        shortDescription = "Your address book",
        whatItAccesses = "Names, phone numbers, email addresses and other details of everyone " +
            "saved in your contacts, plus the accounts on your phone.",
        whyAppsNeedIt = "Finding friends who use the same app, messaging and calling, and " +
            "autofilling contact details.",
        ifYouDeny = "The app can't suggest people you know. You can usually still type a number " +
            "or username yourself.",
        privacyRisk = "This shares other people's information, not just yours. Address books " +
            "are often uploaded to company servers.",
        androidPermissions = listOf(
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS",
        ),
        riskWeight = 3,
    ),
    SMS(
        title = "SMS",
        sensitivity = Sensitivity.CRITICAL,
        shortDescription = "Read and send text messages",
        whatItAccesses = "Your text messages, including one-time passwords (OTPs) from banks " +
            "and other services, and the ability to send messages.",
        whyAppsNeedIt = "Default messaging apps, and some apps that read an OTP automatically " +
            "during sign-up.",
        ifYouDeny = "The app can't read or send texts. You may need to type OTP codes yourself.",
        privacyRisk = "OTPs in SMS protect your bank and other accounts. Only your messaging app " +
            "should normally have this.",
        androidPermissions = listOf(
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.RECEIVE_MMS",
            "android.permission.RECEIVE_WAP_PUSH",
        ),
        riskWeight = 4,
    ),
    PHONE(
        title = "Phone & Call logs",
        sensitivity = Sensitivity.SENSITIVE,
        shortDescription = "Make calls and see call history",
        whatItAccesses = "Making and managing calls, your phone number, network details and, " +
            "for some apps, the list of calls you made and received.",
        whyAppsNeedIt = "Dialer and calling apps, caller ID and spam blocking, and pausing " +
            "music or games when a call comes in.",
        ifYouDeny = "The app can't place calls or identify callers. Most other features keep working.",
        privacyRisk = "Call history shows who you talk to and when.",
        androidPermissions = listOf(
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.CALL_PHONE",
            "android.permission.ANSWER_PHONE_CALLS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.ADD_VOICEMAIL",
            "android.permission.USE_SIP",
            "android.permission.PROCESS_OUTGOING_CALLS",
        ),
        riskWeight = 3,
    ),
    CALENDAR(
        title = "Calendar",
        sensitivity = Sensitivity.SENSITIVE,
        shortDescription = "Your events and schedule",
        whatItAccesses = "Events on your calendars, including titles, times, locations and " +
            "the people invited.",
        whyAppsNeedIt = "Adding bookings or tickets to your calendar, and scheduling or " +
            "productivity apps.",
        ifYouDeny = "The app can't add or read events. You can add them to your calendar yourself.",
        privacyRisk = "Your schedule shows where you will be, when, and who you meet.",
        androidPermissions = listOf(
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
        ),
        riskWeight = 2,
    ),
    BODY_SENSORS(
        title = "Body sensors",
        sensitivity = Sensitivity.SENSITIVE,
        shortDescription = "Heart rate and health sensors",
        whatItAccesses = "Readings from health sensors such as heart rate monitors, on your " +
            "phone or a connected watch.",
        whyAppsNeedIt = "Fitness tracking and health apps.",
        ifYouDeny = "Health readings won't be recorded by the app.",
        privacyRisk = "Health data is very personal and can reveal medical conditions.",
        androidPermissions = listOf(
            "android.permission.BODY_SENSORS",
            "android.permission.BODY_SENSORS_BACKGROUND",
        ),
        backgroundPermission = "android.permission.BODY_SENSORS_BACKGROUND",
        riskWeight = 2,
    ),
    PHYSICAL_ACTIVITY(
        title = "Physical activity",
        sensitivity = Sensitivity.LOW,
        shortDescription = "Steps and movement",
        whatItAccesses = "Whether you are walking, running, cycling or in a vehicle, and your step count.",
        whyAppsNeedIt = "Step counters, fitness apps and automatic 'driving mode' features.",
        ifYouDeny = "Steps and workouts won't be tracked automatically.",
        privacyRisk = "Activity patterns can reveal your routine.",
        androidPermissions = listOf("android.permission.ACTIVITY_RECOGNITION"),
        riskWeight = 1,
    ),
    NEARBY_DEVICES(
        title = "Nearby devices",
        sensitivity = Sensitivity.LOW,
        shortDescription = "Bluetooth and Wi-Fi devices around you",
        whatItAccesses = "Finding and connecting to Bluetooth and Wi-Fi devices near you, such " +
            "as headphones, watches and speakers.",
        whyAppsNeedIt = "Connecting to earbuds, smartwatches, fitness bands, cars and smart-home devices.",
        ifYouDeny = "The app can't find or connect to your accessories.",
        privacyRisk = "Nearby devices can sometimes be used to estimate your location.",
        androidPermissions = listOf(
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.UWB_RANGING",
        ),
        riskWeight = 1,
    ),
    NOTIFICATIONS(
        title = "Notifications",
        sensitivity = Sensitivity.LOW,
        shortDescription = "Show alerts",
        whatItAccesses = "Showing alerts, banners and badges on your phone.",
        whyAppsNeedIt = "Messages, reminders, delivery updates and news.",
        ifYouDeny = "You won't get alerts from the app; you'll need to open it to see updates.",
        privacyRisk = "Low privacy risk, but too many notifications can be distracting.",
        androidPermissions = listOf("android.permission.POST_NOTIFICATIONS"),
        riskWeight = 0,
    );

    companion object {
        private val byPermission: Map<String, PermissionCategory> =
            entries.flatMap { cat -> cat.androidPermissions.map { it to cat } }.toMap()

        /** Returns the category an Android permission belongs to, or null if we don't track it. */
        fun forAndroidPermission(permission: String): PermissionCategory? = byPermission[permission]
    }
}
