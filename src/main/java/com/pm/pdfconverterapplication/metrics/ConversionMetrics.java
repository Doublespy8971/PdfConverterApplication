package com.pm.pdfconverterapplication.metrics;

import com.pm.pdfconverterapplication.service.LibreOfficeConverterService;
import com.pm.pdfconverterapplication.service.TaskRegistryService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ConversionMetrics {

    public ConversionMetrics(MeterRegistry registry,
                             TaskRegistryService taskRegistryService,
                             LibreOfficeConverterService libreOfficeConverterService) {
        Gauge.builder("task_registry_size", taskRegistryService, TaskRegistryService::getTaskCount)
                .description("Number of tasks currently held in the in-memory registry")
                .register(registry);
        Gauge.builder("task_registry_stored_bytes", taskRegistryService, TaskRegistryService::getStoredResultBytes)
                .description("Bytes retained by completed task results")
                .register(registry);
        Gauge.builder("libreoffice_permits_in_use", libreOfficeConverterService,
                        LibreOfficeConverterService::getPermitsInUse)
                .description("LibreOffice conversion permits currently in use")
                .register(registry);
    }
}
