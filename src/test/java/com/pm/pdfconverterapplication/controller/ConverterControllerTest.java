package com.pm.pdfconverterapplication.controller;

import com.pm.pdfconverterapplication.service.AsyncConversionWorker;
import com.pm.pdfconverterapplication.service.ConversionService;
import com.pm.pdfconverterapplication.service.TaskRegistryService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConverterController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "spring.servlet.multipart.max-file-size=1B")
@Import(ConverterControllerTest.StubWorkerConfiguration.class)
class ConverterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubTaskRegistry taskRegistryService;

    @Autowired
    private StubConversionService conversionService;

    @TestConfiguration
    static class StubWorkerConfiguration {
        @Bean
        AsyncConversionWorker asyncConversionWorker() {
            return new AsyncConversionWorker() {
                @Override
                public void convertFileAsync(String filePath, String originalFilename, String tool, String taskId) {
                }
            };
        }

        @Bean
        StubTaskRegistry taskRegistryService() {
            return new StubTaskRegistry();
        }

        @Bean
        StubConversionService conversionService() {
            return new StubConversionService();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    static class StubTaskRegistry extends TaskRegistryService {
        private String taskId;
        private TaskStatus task;
        private boolean atCapacity;

        StubTaskRegistry() {
            super(1, 1);
        }

        @Override
        public String initiateTask() {
            if (atCapacity) {
                throw new TaskRegistryService.TaskCapacityExceededException();
            }
            taskId = "task-1";
            task = new TaskStatus(1);
            return taskId;
        }

        @Override
        public boolean taskExists(String requestedTaskId) {
            return taskId != null && taskId.equals(requestedTaskId);
        }

        @Override
        public TaskStatus getTask(String requestedTaskId) {
            return task;
        }
    }

    static class StubConversionService extends ConversionService {
        private boolean reject;

        @Override
        public void validateConversionRequest(org.springframework.web.multipart.MultipartFile file, String tool) {
            if (reject) {
                throw new IllegalArgumentException("Unsupported file extension");
            }
        }
    }

    @Test
    void asyncFlowPollsAndDownloadsResult() throws Exception {
        String taskId = "task-1";
        TaskRegistryService.TaskStatus status = new TaskRegistryService.TaskStatus(1);
        taskRegistryService.taskId = taskId;
        taskRegistryService.task = status;

        MockMultipartFile file = new MockMultipartFile("file", "input.pdf", "application/pdf", new byte[]{1});
        mockMvc.perform(multipart("/api/convert/pdf-to-word").file(file))
                .andExpect(status().isAccepted())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(taskId)));

        taskRegistryService.task = status;
        mockMvc.perform(get("/api/convert/status/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PENDING")));

        status.setResultContent(new byte[]{9, 8});
        status.setFileName("output.docx");
        status.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        status.setStatus("COMPLETED");
        mockMvc.perform(get("/api/convert/download/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("output.docx")))
                .andExpect(content().bytes(new byte[]{9, 8}));
    }

    @Test
    void unknownTaskIdReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/convert/status/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsBadExtensionAndEmptyFile() throws Exception {
        conversionService.reject = true;

        mockMvc.perform(multipart("/api/convert/pdf-to-word")
                        .file(new MockMultipartFile("file", "input.txt", "text/plain", new byte[]{1})))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Unsupported file extension"));

        mockMvc.perform(multipart("/api/convert/pdf-to-word")
                        .file(new MockMultipartFile("file", "input.pdf", "application/pdf", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("File is empty"));
    }

    @Test
    void rejectsOversizedFileWithCleanPayloadTooLargeResponse() throws Exception {
        mockMvc.perform(multipart("/api/convert/pdf-to-word")
                        .file(new MockMultipartFile("file", "input.pdf", "application/pdf", new byte[]{1, 2})))
                .andExpect(status().is(HttpStatus.PAYLOAD_TOO_LARGE.value()))
                .andExpect(content().string("File exceeds the maximum allowed size"));
    }

    @Test
    void capacityReturnsRetryableServiceUnavailable() throws Exception {
        taskRegistryService.atCapacity = true;

        mockMvc.perform(multipart("/api/convert/pdf-to-word")
                        .file(new MockMultipartFile("file", "input.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void batchRejectsUnknownToolBeforeCreatingTask() throws Exception {
        taskRegistryService.taskId = null;
        mockMvc.perform(multipart("/api/convert/batch/not-a-tool")
                        .file(new MockMultipartFile("files", "input.pdf", "application/pdf", new byte[]{1})))
                .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertNull(taskRegistryService.taskId);
    }
}
