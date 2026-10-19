package lk.live.aplusewings;

import android.content.Intent;
import android.net.Uri;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the "open in app" custom-scheme URIs emitted by the four brand websites:
 *
 *   aplus-player://lessonId=<id>&url=<passcode>
 *   ewings-player://lessonId=<id>&url=<passcode>
 *   sailingpen-player://lessonId=<id>&url=<passcode>     (ComMaths lesson handoff)
 *   ims-media-player://lessonId=<id>&url=<passcode>      (IMSEdu lesson handoff)
 *
 *   aplus-player://callback?token=<accessToken>
 *   ewings-player://callback?token=<accessToken>
 *   combinedmaths-player://callback?token=<accessToken>  (ComMaths auth callback)
 *   ims-player://callback?token=<accessToken>            (IMSEdu auth callback)
 *
 * These links are totally non-standard URIs (the "authority" contains "=" and "&"),
 * so we parse them with regex rather than trusting Uri.getQueryParameter alone.
 */
public final class DeepLinkRouter {

    public static final String KIND_LESSON = "lesson";
    public static final String KIND_CALLBACK = "callback";

    public static final class Result {
        public final String brand;      // one of: Aplus, Ewings, ComMaths, IMSEdu
        public final String kind;       // KIND_LESSON or KIND_CALLBACK
        public final String lessonId;   // may be null
        public final String code;       // link_params / passcode, may be null
        public final String token;      // may be null

        Result(String brand, String kind, String lessonId, String code, String token) {
            this.brand = brand;
            this.kind = kind;
            this.lessonId = lessonId;
            this.code = code;
            this.token = token;
        }
    }

    private static final Map<String, String> SCHEME_TO_BRAND = new HashMap<String, String>();
    static {
        // Lesson schemes
        SCHEME_TO_BRAND.put("aplus-player", "Aplus");
        SCHEME_TO_BRAND.put("ewings-player", "Ewings");
        SCHEME_TO_BRAND.put("sailingpen-player", "ComMaths");
        SCHEME_TO_BRAND.put("ims-media-player", "IMSEdu");
        // Callback-only schemes
        SCHEME_TO_BRAND.put("combinedmaths-player", "ComMaths");
        SCHEME_TO_BRAND.put("ims-player", "IMSEdu");
    }

    private DeepLinkRouter() {}

    /** Try to parse the VIEW intent as one of our known deep links. */
    public static Result parse(Intent intent) {
        if (intent == null) return null;
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) return null;
        Uri uri = intent.getData();
        if (uri == null) return null;

        String scheme = uri.getScheme();
        if (scheme == null) return null;
        String brand = SCHEME_TO_BRAND.get(scheme.toLowerCase());
        if (brand == null) return null;

        String raw = uri.toString();

        // Callback flavor: <scheme>://callback?token=<...>
        // Match on the AUTHORITY (host) component so a passcode that happens to
        // contain the literal substring "callback" doesn't get misrouted.
        String host = uri.getHost();
        if (host == null) {
            // Non-standard URI (e.g. "aplus-player://lessonId=X&url=Y" doesn't
            // have a parseable host). Fall back to parsing the authority-ish
            // part manually.
            String auth = uri.getAuthority();
            if (auth != null && auth.toLowerCase().startsWith("callback")) host = "callback";
        }
        boolean isCallback = "callback".equalsIgnoreCase(host);
        if (isCallback) {
            String token = extract(raw, "token");
            if (token == null || token.length() == 0) return null;
            return new Result(brand, KIND_CALLBACK, null, null, token);
        }

        // Lesson flavor: <scheme>://lessonId=<id>&url=<passcode>
        String lessonId = extract(raw, "lessonId");
        String code = extract(raw, "url");
        if (lessonId == null && code == null) return null;
        return new Result(brand, KIND_LESSON, lessonId, code, null);
    }

    /** Extract &key=value or ?key=value from a raw deep-link URI, URL-decoding the value. */
    private static String extract(String raw, String key) {
        Pattern p = Pattern.compile("[?&/=]" + Pattern.quote(key) + "=([^&#]*)");
        Matcher m = p.matcher(raw);
        if (!m.find()) return null;
        String v = m.group(1);
        try {
            return URLDecoder.decode(v, "UTF-8");
        } catch (Exception e) {
            return v;
        }
    }
}
