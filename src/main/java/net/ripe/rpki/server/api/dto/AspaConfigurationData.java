package net.ripe.rpki.server.api.dto;

import com.google.gson.Gson;
import lombok.NonNull;
import lombok.Value;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.util.Streams;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.ripe.rpki.util.Streams.streamToSortedMap;

@Value
public class AspaConfigurationData {

    private static final Gson GSON = new Gson();

    @NonNull
    Asn customerAsn;

    /**
     * Use a list of providers so we can perform additional validation and explicitly reject duplicate values.
     * <emph>The entity restricts this to a Set w/ unique constraints in the database.</emph>
     */
    @NonNull
    List<Asn> providers;

    public static String entityTag(SortedMap<Asn, SortedSet<Asn>> aspaConfiguration) {
        String json = GSON.toJson(aspaConfiguration);
        return Streams.entityTag(Stream.of(json.getBytes(StandardCharsets.UTF_8)));
    }

    public static SortedMap<Asn, SortedSet<Asn>> dataToMaps(List<AspaConfigurationData> configuration) {
        return streamToSortedMap(
            configuration.stream(),
            AspaConfigurationData::getCustomerAsn,
            ac -> new TreeSet<>(ac.getProviders())
        );
    }

    public static String inIETFNotation(Asn customer, Collection<Asn> providers) {
        return providers.stream()
                .sorted(Comparator.comparing(Asn::longValue))
                .map(Object::toString)
                .collect(Collectors.joining(", ", customer + " => [", "]"));
    }

    public static String inIETFNotation(AspaConfigurationData aspa) {
        return inIETFNotation(aspa.getCustomerAsn(), aspa.getProviders());
    }
}
