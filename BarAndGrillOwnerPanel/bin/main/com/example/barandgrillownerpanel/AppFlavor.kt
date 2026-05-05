package com.example.barandgrillownerpanel

/**
 * AppFlavor controls the identity of the POS app at runtime.
 * Now unified under the KLIX brand.
 */
enum class AppFlavor(
    val appName: String,
    val windowTitle: String,
    val iconResource: String,
    val showFoodCategories: Boolean,
    val autoSelectBranchName: String?
) {
    KLIX(
        appName = "KLIX Dashboard",
        windowTitle = "KLIX Dashboard",
        iconResource = "icon_klix.png",
        showFoodCategories = true,
        autoSelectBranchName = null
    );

    companion object {
        /**
         * The active flavor for this runtime instance.
         */
        val current: AppFlavor = KLIX
    }
}
