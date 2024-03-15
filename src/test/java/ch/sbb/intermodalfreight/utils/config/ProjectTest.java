package ch.sbb.intermodalfreight.utils.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ProjectTest {

    private Project project;

    @BeforeEach
    void setUp() {
        project = new Project();
    }

    @Nested
    class EnsureDirectoryExists {

        @Test
        void testWhenDirectoryDoesNotExist_ShouldCreateDirectory(@TempDir Path tempDir) {
            Path testDir = tempDir.resolve("testDir");
            project.ensureDirectoryExists(testDir);
            assertThat(Files.exists(testDir)).isTrue();
            assertThat(Files.isDirectory(testDir)).isTrue();
        }

        @Test
        void testWhenDirectoryExistsAndOverwritingAllowed_ShouldOverwrite(@TempDir Path tempDir) throws IOException {
            Path testDir = tempDir.resolve("testDir");
            Files.createDirectory(testDir);
            project.setOverwrite(true);
            project.ensureDirectoryExists(testDir);
            assertThat(Files.exists(testDir)).isTrue();
            assertThat(Files.isDirectory(testDir)).isTrue();
        }

        @Test
        void testWhenDirectoryExistsAndOverwritingNotAllowed_ShouldThrowException(@TempDir Path tempDir) throws IOException {
            Path testDir = tempDir.resolve("testDir");
            Files.createDirectory(testDir);
            project.setOverwrite(false);
            assertThrows(RuntimeException.class, () -> project.ensureDirectoryExists(testDir));
        }
    }

    @Nested
    class DirectoryPaths {

        @Test
        void testGetRawDataPath() {
            assertThat(project.getDirectory().getOriginalDataPath()).isEqualTo(
                    Path.of("test/output/ch/sbb/intermodalFreight/runExampleIT/01_original_data"));
        }

        @Test
        void testGetMatsimInputPath() {
            assertThat(project.getDirectory().getMatsimInputPath()).isEqualTo(
                    Path.of("test/output/ch/sbb/intermodalFreight/runExampleIT/03_matsim_input/v001"));
        }

        @Test
        void testGetMatsimConfigFilePath() {
            assertThat(project.getDirectory().getMatsimConfigFilePath()).isEqualTo(
                    Path.of("test/output/ch/sbb/intermodalFreight/runExampleIT/04_matsim_config/config_rTest.xml"));
        }

        @Test
        void testGetMatsimOutputPath() {
            assertThat(project.getDirectory().getMatsimOutputPath()).isEqualTo(
                    Path.of("test/output/ch/sbb/intermodalFreight/runExampleIT/05_matsim_output/rTest"));
        }

        @Test
        void testGetAnalysisResultsPath() {
            assertThat(project.getDirectory().getAnalysisResultsPath()).isEqualTo(
                    Path.of("test/output/ch/sbb/intermodalFreight/runExampleIT/06_analysis_results/rTest"));
        }

        @Test
        void testGetDocsPath() {
            assertThat(project.getDirectory().getDocsPath()).isEqualTo(
                    Path.of("test/output/ch/sbb/intermodalFreight/runExampleIT/07_docs"));
        }
    }

    @Nested
    class MatsimInput {

        @Test
        void testGetMatsimInputVersion() {
            assertThat(project.getMatsimInput().getVersion()).isEqualTo("v001");
        }

        @Nested
        class Supply {

            @Test
            void testGetCraneLinkLength() {
                assertThat(project.getMatsimInput().getSupply().getCraneLinkLength()).isEqualTo(600);
            }

            @Test
            void testGetCraneTravelTime() {
                assertThat(project.getMatsimInput().getSupply().getCraneTravelTime()).isEqualTo(120);
            }
        }
    }

    @Nested
    class MatsimRun {
        @Test
        void testGetRunId() {
            assertThat(project.getMatsimRun().getId()).isEqualTo("rTest");
        }
    }

    @Nested
    class VersionFile {

        @Test
        void testWriteMatsimInputVersionFileWriterIsCalledOnce() throws IOException {
            Writer mockedWriter = Mockito.mock(BufferedWriter.class);
            project.setWriter(mockedWriter);
            project.writeMatsimInputVersionFile();
            verify(mockedWriter, times(1)).write(any(char[].class), any(int.class), any(int.class));
        }

        @Test
        void testWriteMatsimInputVersionFileContent() {
            StringWriter stringWriter = new StringWriter();
            project.setWriter(stringWriter);
            project.writeMatsimInputVersionFile();
            String jsonOutput = stringWriter.toString();
            assertThat(jsonOutput).isNotEmpty();
            assertThat(jsonOutput).contains("createdBy");
            assertThat(jsonOutput).contains("createdAt");
            assertThat(jsonOutput).contains("matsimInput");
        }
    }
}
