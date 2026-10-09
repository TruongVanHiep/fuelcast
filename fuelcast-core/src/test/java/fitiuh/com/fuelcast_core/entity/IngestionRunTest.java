package fitiuh.com.fuelcast_core.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionRunTest {

    /**
     * startedAt phải được gán sẵn: Hibernate INSERT cột này tường minh nên
     * DEFAULT now() của DB không cứu được, null sẽ vi phạm NOT NULL.
     */
    @Test
    void newRunStartsRunningWithStartTimeAndNoRows() {
        IngestionRun run = IngestionRun.builder()
                .source("MOIT_BULLETIN")
                .targetUrl("https://moit.gov.vn/tin-tuc/x.html")
                .build();

        assertThat(run.getStatus()).isEqualTo(IngestionStatus.RUNNING);
        assertThat(run.getStartedAt()).isNotNull();
        assertThat(run.getFinishedAt()).isNull();
        assertThat(run.getRowsIngested()).isZero();
    }

    @Test
    void succeedRecordsRowCountAndFinishTime() {
        IngestionRun run = IngestionRun.builder().source("MOIT_BULLETIN").build();

        run.succeed(5);

        assertThat(run.getStatus()).isEqualTo(IngestionStatus.SUCCESS);
        assertThat(run.getRowsIngested()).isEqualTo(5);
        assertThat(run.getFinishedAt()).isNotNull();
    }

    @Test
    void failKeepsTheReasonAndFinishTime() {
        IngestionRun run = IngestionRun.builder().source("MOIT_BULLETIN").build();

        run.fail("parser không thấy mặt hàng nào");

        assertThat(run.getStatus()).isEqualTo(IngestionStatus.FAILED);
        assertThat(run.getErrorMessage()).isEqualTo("parser không thấy mặt hàng nào");
        assertThat(run.getFinishedAt()).isNotNull();
    }
}
