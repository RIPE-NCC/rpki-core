package net.ripe.rpki.bgpris.riswhois;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.ipresource.IpRange;
import net.ripe.rpki.server.api.dto.BgpRisEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class RisWhoisParser {
    /*
     * This regular expression doesn't match prefixes originating from AS SETS. The RPKI doesn't
     * support these anyway. Maybe in the future we should warn ROA administrators when they have
     * resources using AS SETS...
     */
    private static final Pattern ENTRY_PATTERN = Pattern.compile("^([0-9]+)\t([0-9a-fA-F.:/]+)\t([0-9]+)$", Pattern.MULTILINE);

    private RisWhoisParser() {
    }

    public static List<BgpRisEntry> parse(String risWhoisDump) {
        ArrayList<BgpRisEntry> result = new ArrayList<>();

        Matcher matcher = ENTRY_PATTERN.matcher(risWhoisDump);
        int rejectedLines = 0;
        while (matcher.find()) {
            try {
                Asn origin = Asn.parse(matcher.group(1));
                IpRange prefix = IpRange.parse(matcher.group(2));
                int visibility = Integer.parseInt(matcher.group(3));
                result.add(new BgpRisEntry(origin, prefix, visibility));
            } catch(IllegalArgumentException e) {
                log.error("Unable to parse BGP dump entry", e);
                rejectedLines++;
            } catch (ArrayIndexOutOfBoundsException e) {
                rejectedLines++;
            }
        }

        if (rejectedLines > 0) {
            log.error("RisWhoisParser rejected {} lines (incomplete/invalid)", rejectedLines);
        }

        return result;
    }
}
