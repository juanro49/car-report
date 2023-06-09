/*
 * Copyright 2015 Jan Kühle
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.juanro.autumandu.data.report;

import lecho.lib.hellocharts.formatter.AxisValueFormatter;
import lecho.lib.hellocharts.model.AxisValue;

public abstract class ReportAxisValueFormatter implements AxisValueFormatter {
    public abstract String format(float value);

    @Override
    public int formatValueForManualAxis(char[] formattedValue, AxisValue axisValue) {
        return copyLabelToFormattedValue(format(axisValue.getValue()), formattedValue);
    }

    @Override
    public int formatValueForAutoGeneratedAxis(char[] formattedValue, float value, int autoDecimalDigits) {
        return copyLabelToFormattedValue(format(value), formattedValue);
    }

    private int copyLabelToFormattedValue(String label, char[] formattedValue) {
        char[] charLabel = label.toCharArray();
        int length = Math.min(charLabel.length, formattedValue.length);

        System.arraycopy(charLabel, 0, formattedValue, formattedValue.length - length, length);

        return length;
    }
}
