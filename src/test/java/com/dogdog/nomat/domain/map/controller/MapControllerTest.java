package com.dogdog.nomat.domain.map.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.dogdog.nomat.domain.comment.service.CommentService;
import com.dogdog.nomat.domain.map.service.MapService;
import com.dogdog.nomat.domain.report.service.ReportService;
import com.dogdog.nomat.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

class MapControllerTest {

    private final MapController mapController = new MapController(
            mock(MapService.class),
            mock(CommentService.class),
            mock(ReportService.class)
    );

    @Test
    void getMapsRequiresAuthenticationWhenFilteringByCreator() {
        assertThatThrownBy(() -> mapController.getMaps(
                null,
                null,
                null,
                null,
                0,
                12,
                "latest",
                1L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invalid_token");
    }
}
