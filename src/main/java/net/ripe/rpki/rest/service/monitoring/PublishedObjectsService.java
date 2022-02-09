package net.ripe.rpki.rest.service.monitoring;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import net.ripe.rpki.domain.PublicationStatus;
import net.ripe.rpki.domain.PublishedObjectEntry;
import net.ripe.rpki.domain.PublishedObjectRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@AllArgsConstructor
@Tag(name = "/api/monitoring/published-objects")
@Controller
@Transactional(readOnly = true)
public class PublishedObjectsService {
    private PublishedObjectRepository publishedObjectRepository;

    // TODO: Remove the old URL alias when rpki-monitoring no longer consumes the old one.
   @Operation(summary = "Get all the published objects")
    @GetMapping(value = {"/api/monitoring/published-objects", "/api/published-objects"})
    public ResponseEntity<List<PublishedObjectEntry>> listPublishedObjects() {
        final List<PublishedObjectEntry> published = publishedObjectRepository.findEntriesByPublicationStatus(PublicationStatus.PUBLISHED_STATUSES);

        return ResponseEntity.ok(published);
    }
}
