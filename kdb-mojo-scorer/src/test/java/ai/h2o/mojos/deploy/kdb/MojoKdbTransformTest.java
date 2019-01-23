package ai.h2o.mojos.deploy.kdb;

import org.junit.jupiter.api.Test;
import static com.google.common.truth.Truth.assertThat;
import ai.h2o.mojos.runtime.MojoPipeline;
import ai.h2o.mojos.runtime.frame.MojoFrame;
import ai.h2o.mojos.runtime.lic.LicenseException;
import kx.c;
import java.io.IOException;


class MojoKdbTransformTest {

    private static MojoFrame iframe;
    private static MojoPipeline model;
    private static c.Flip kdbFlipTable;

    @Test
    void validateMojoTransform() throws LicenseException, IOException {
        String dropCols = "";
        model = MojoPipeline.loadFrom("src/test/resources/pipeline.mojo");
        kdbFlipTable = generateDummyFlip();
        iframe = MojoKdbTransform.createMojoFrameFromKdbFlip(model, kdbFlipTable, dropCols);
        iframe.debug();
        assertThat(iframe.getNcols() == 23);
        assertThat(iframe.getNrows() == 2);
    }

    @Test
    void validateKdbPublishObjectGeneration() {
        String pubTable = "fooTable";
        MojoFrame oframe = model.transform(iframe);
        oframe.debug();
        Object[] kdbPublishObject = MojoKdbTransform.generateMojoPredictionPublishObject(pubTable, oframe, kdbFlipTable);
        assertThat(kdbPublishObject.length == 3);
        assertThat(kdbPublishObject[0].equals(".u.upd"));
        assertThat(kdbPublishObject[1].equals("fooTable"));
        assertThat(kdbPublishObject[2] instanceof Object[]);
    }

    private c.Flip generateDummyFlip() {
        int[] limBal = {20000, 120000};
        int[] sex = {1, 2};
        int[] education = {5, 1};
        int[] marriage = {3,0};
        int[] age = {25, 64};
        int[] pay1 = {2, -1};
        int[] pay2 = {2, 2};
        int[] pay3 = {-1, 0};
        int[] pay4 = {-1, 0};
        int[] pay5 = {-2, 0};
        int[] pay6 = {-2, 2};
        int[] bill1 = {3913, 2682};
        int[] bill2 = {3102, 1725};
        int[] bill3 = {689, 2682};
        int[] bill4 = {0, 3272};
        int[] bill5 = {0, 3455};
        int[] bill6 = {0, 3261};
        int[] payamt1 = {0, 0};
        int[] payamt2 = {689, 1000};
        int[] payamt3 = {0, 1000};
        int[] payamt4 = {0, 1000};
        int[] payamt5 = {0, 0};
        int[] payamt6 = {0, 2000};
        Object[] data = new Object[] {limBal, sex, education, marriage, age, pay1, pay2, pay3, pay4, pay5, pay6, bill1, bill2, bill3, bill4, bill5, bill6,
                payamt1, payamt2, payamt3, payamt4, payamt5, payamt6};
        String[] columnNames = new String[] {"LIMIT_BAL", "SEX", "EDUCATION", "MARRIAGE", "AGE", "PAY_1",
                "PAY_2", "PAY_3", "PAY_4", "PAY_5", "PAY_6", "BILL_AMT1", "BILL_AMT2", "BILL_AMT3", "BILL_AMT4",
                "BILL_AMT5", "BILL_AMT6", "PAY_AMT1", "PAY_AMT2", "PAY_AMT3", "PAY_AMT4", "PAY_AMT5", "PAY_AMT6"};
        c.Dict dict = new c.Dict(columnNames, data);
        return new c.Flip(dict);
    }
}