package net.ripe.rpki.rest.service;

import com.google.common.collect.Sets;
import lombok.experimental.UtilityClass;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.server.api.dto.AspaConfigurationData;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class Aspas {

    private static Map<Asn, Set<Asn>> toMap(List<AspaConfigurationData> aspas) {
        return aspas.stream()
                .collect(Collectors.toMap(AspaConfigurationData::getCustomerAsn,
                        a -> Set.copyOf(a.getProviders()),
                        Sets::union));
    }

    public static Map<Asn, AspaDiff> diffPerCustomer(List<AspaConfigurationData> oldAspas, List<AspaConfigurationData> newAspas) {
        var olds = toMap(oldAspas);
        var news = toMap(newAspas);

        var added = news.entrySet().stream()
                .filter(e -> !e.getValue().equals(olds.get(e.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> new AspaDiff(e.getValue(), Collections.emptySet())));

        var deleted = olds.entrySet().stream()
                .filter(e -> !e.getValue().equals(news.get(e.getKey())))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> new AspaDiff(Collections.emptySet(), e.getValue())));

        deleted.forEach((asn, diff) -> added.merge(asn, diff, AspaDiff::combine));
        return added;
    }


    public record AspaDiff(Set<Asn> added, Set<Asn> deleted) {
        static AspaDiff combine(AspaDiff diff1, AspaDiff diff2) {
            return new AspaDiff(
                    Sets.union(diff1.added, diff2.added),
                    Sets.union(diff1.deleted, diff2.deleted));
        }
    }
}
