package net.ripe.rpki.server.api.dto;

import com.google.gson.Gson;
import lombok.NonNull;
import lombok.Value;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.util.Streams;

import javax.validation.constraints.NotEmpty;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.SortedMap;
import java.util.stream.Stream;

import static net.ripe.rpki.util.Streams.streamToSortedMap;

@Value
public class AspaConfigurationData {

    private static final Gson GSON = new Gson();

    @NonNull
    Asn customerAsn;

    @NonNull
    @NotEmpty
    List<AspaProviderData> providers;

    public static String entityTag(SortedMap<Asn, SortedMap<Asn, AspaAfiLimit>> aspaConfiguration) {
        String json = GSON.toJson(aspaConfiguration);
        return Streams.entityTag(Stream.of(json.getBytes(StandardCharsets.UTF_8)));
    }

    public static SortedMap<Asn, SortedMap<Asn, AspaAfiLimit>> dataToMaps(List<AspaConfigurationData> configuration) {
        return streamToSortedMap(
            configuration.stream(),
            AspaConfigurationData::getCustomerAsn,
            aspaConfiguration -> streamToSortedMap(
                aspaConfiguration.getProviders().stream(),
                AspaProviderData::getProviderAsn,
                AspaProviderData::getAfiLimit
            )
        );
    }
}
