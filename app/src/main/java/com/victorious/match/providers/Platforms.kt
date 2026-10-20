package com.victorious.match.providers

/** The ten platforms. Names are used descriptively only; no affiliation or endorsement is implied. */
object Platforms {
    val all = listOf(
        PlatformInfo("tinder", "Tinder", "com.tinder", "https://www.help.tinder.com/hc/en-us/articles/115005626726",
            "Sign in at account.gotinder.com/download and request your data. You get an email link to a ZIP (expires in 24 h). Import the ZIP or data.json.", "ZIP with data.json (supported parser)"),
        PlatformInfo("hinge", "Hinge", "co.hinge.app", "https://hingeapp.zendesk.com/hc/en-us/articles/360004792234-How-can-I-make-a-Data-Request",
            "In Hinge: Settings → Download My Data. Ready within 30 days.", "Undocumented; generic import"),
        PlatformInfo("bumble", "Bumble", "com.bumble.app", "https://support.bumble.com/hc/en-us/articles/28783199493917-Requesting-your-data",
            "Contact Bumble support in-app and choose \"Request data\". Up to 1 month.", "Undocumented; generic import"),
        PlatformInfo("okcupid", "OkCupid", "com.okcupid.okcupid", "https://okcupid-app.zendesk.com/hc/en-us/articles/44307421281179",
            "Make an access/portability request via the OkCupid privacy policy contact process.", "Undocumented; generic import"),
        PlatformInfo("match", "Match", "com.match.android.matchmobile", "https://help.match.com/hc/en-us/articles/38345081720603-Requesting-Your-Data",
            "Submit a request at help.match.com: Contact Us → I have a Safety or Privacy concern → I want to access my personal data. After identity verification you get a link to your data. Messages you received are excluded.", "Undocumented; generic import"),
        PlatformInfo("pof", "Plenty of Fish", "com.pof.android", "https://help.pof.com/hc/en-us/articles/360034158171-Your-privacy-rights",
            "Use POF's legal contact form: \"I have a safety or privacy concern\".", "Undocumented; generic import"),
        PlatformInfo("grindr", "Grindr", "com.grindrapp.android", "https://help.grindr.com/hc/en-us/articles/1500005257961-Accessing-your-personal-data",
            "In Grindr: Settings → Download My Data. You receive a device ZIP plus a server-data ZIP by email.", "ZIP; generic import"),
        PlatformInfo("feeld", "Feeld", "co.feeld", "https://feeld.co/about/privacy",
            "Contact Feeld to exercise your access/portability right (machine-readable format).", "Undocumented; generic import"),
        PlatformInfo("cmb", "Coffee Meets Bagel", "com.coffeemeetsbagel", "https://coffeemeetsbagel.zendesk.com/hc/en-us/articles/360020881054",
            "Submit a support request from your account email. Up to 30 days; not available after profile deletion.", "Undocumented; generic import"),
        PlatformInfo("badoo", "Badoo", "com.badoo.mobile", "https://support.badoo.com/hc/en-us/articles/32092182348445-Requesting-your-data",
            "Contact Badoo support and request your data (same SAR process as Bumble).", "Undocumented; generic import"),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }
}

object ProviderRegistry {
    val providers: List<DatingProvider> = Platforms.all.map { if (it.id == "tinder") TinderImportProvider(it) else GenericImportProvider(it) }
    fun get(id: String) = providers.first { it.info.id == id }
}
