package org.potiguaras.supabased.helpers
import com.google.appinventor.components.common.OptionList

enum class ThirdPartyProvider(private val value: Int) : OptionList<Int> {
    Google(1),
    GitHub(2),
    Facebook(3),
    Twitter(4),
    Apple(5),
    Discord(6),
    Twitch(7),
    Spotify(8),
    Slack(9),
    Bitbucket(10),
    GitLab(11),
    Azure(12),
    Notion(13),
    Zoom(14);

    override fun toUnderlyingValue(): Int = value
    companion object {
        private val lookup: Map<Int, ThirdPartyProvider> by lazy {
            entries.associateBy { it.toUnderlyingValue() }
        }
        fun fromUnderlyingValue(value: Int): ThirdPartyProvider? = lookup[value]
    }
}