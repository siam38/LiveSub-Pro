package com.charles.livecaptionn.update

data class UpdateInfo(

    val tagName: String,

    val releaseName: String,

    val buildNumber: Int,

    val apkDownloadUrl: String?,

    val releasePageUrl: String,

    val notes: String
)
