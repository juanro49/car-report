/*
 * Copyright 2012 Jan Kühle
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.kuehle.carreport.data.report;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import me.kuehle.carreport.Preferences;
import me.kuehle.carreport.R;
import me.kuehle.carreport.db.FuelType;
import me.kuehle.carreport.db.Refueling;
import me.kuehle.carreport.gui.util.SectionListAdapter.Section;
import me.kuehle.chartlib.axis.DecimalAxisLabelFormatter;
import me.kuehle.chartlib.chart.Chart;
import me.kuehle.chartlib.data.Dataset;
import me.kuehle.chartlib.data.PointD;
import me.kuehle.chartlib.data.Series;
import me.kuehle.chartlib.renderer.LineRenderer;
import me.kuehle.chartlib.renderer.OnClickListener;
import me.kuehle.chartlib.renderer.RendererList;
import android.content.Context;
import android.graphics.Color;
import android.text.format.DateFormat;
import android.widget.Toast;

public class FuelPriceReport extends AbstractReport {
	private class CalculableItem extends ReportData.AbstractCalculableItem {
		private static final String FORMAT_NORMAL = "%.3f %s";
		private static final String FORMAT_CALCULATION = "%.2f %s";
		private double value;
		private String[] calcLabels;

		public CalculableItem(String label, double value) {
			this(label, value, new String[] { label, label });
		}

		public CalculableItem(String label, double value, String[] calcLabels) {
			super(label, String.format(FORMAT_NORMAL, value, unit));
			this.value = value;
			this.calcLabels = calcLabels;
		}

		@Override
		public void applyCalculation(double value1, int option) {
			Preferences prefs = new Preferences(context);
			if (option == 0) {
				double result = value * value1;
				setValue(String.format(FORMAT_CALCULATION, result,
						prefs.getUnitCurrency()));
			} else if (option == 1) {
				double result = value1 / value;
				setValue(String.format(FORMAT_CALCULATION, result,
						prefs.getUnitVolume()));
			}
			setLabel(calcLabels[option]);
		}
	}

	private class ReportGraphData extends AbstractReportGraphData {
		public ReportGraphData(Context context, FuelType fuelType, int color) {
			super(context, fuelType.name, color);

			List<Refueling> refuelings = fuelType.refuelings();
			for (Refueling refueling : refuelings) {
				xValues.add(refueling.date.getTime());
				yValues.add((double) refueling.getFuelPrice());
			}
		}
	}

	private ArrayList<ReportGraphData> reportData;
	private String unit;

	public FuelPriceReport(Context context) {
		super(context);

		Preferences prefs = new Preferences(context);
		unit = String.format("%s/%s", prefs.getUnitCurrency(),
				prefs.getUnitVolume());

		List<FuelType> fuelTypes = FuelType.getAll();

		float[] hsvColor = new float[3];
		Color.colorToHSV(
				context.getResources().getColor(android.R.color.holo_blue_dark),
				hsvColor);

		reportData = new ArrayList<FuelPriceReport.ReportGraphData>();
		for (FuelType fuelType : fuelTypes) {
			int color = Color.HSVToColor(hsvColor);
			ReportGraphData data = new ReportGraphData(context, fuelType, color);
			if (!data.isEmpty()) {
				reportData.add(data);

				Series series = data.getSeries();
				double avg = 0;
				for (int i = 0; i < series.size(); i++) {
					avg += series.get(i).y;
				}
				avg /= series.size();

				Section section = addDataSection(fuelType.name, color);
				section.addItem(new CalculableItem(context
						.getString(R.string.report_highest), series.maxY(),
						new String[] {
								context.getString(R.string.report_at_most),
								context.getString(R.string.report_at_least) }));
				section.addItem(new CalculableItem(context
						.getString(R.string.report_lowest), series.minY(),
						new String[] {
								context.getString(R.string.report_at_least),
								context.getString(R.string.report_at_most) }));
				section.addItem(new CalculableItem(context
						.getString(R.string.report_average), avg));

				hsvColor[0] += 20;
				if (hsvColor[0] > 360) {
					hsvColor[0] -= 360;
				}
			}
		}
	}

	@Override
	public CalculationOption[] getCalculationOptions() {
		Preferences prefs = new Preferences(context);
		return new CalculationOption[] {
				new CalculationOption(context.getString(
						R.string.report_calc_vol2price_name,
						prefs.getUnitVolume()), prefs.getUnitVolume()),
				new CalculationOption(context.getString(
						R.string.report_calc_price2vol_name,
						prefs.getUnitVolume(), prefs.getUnitCurrency()),
						prefs.getUnitCurrency()) };
	}

	@Override
	public Chart getChart(int option) {
		final Dataset dataset = new Dataset();
		RendererList renderers = new RendererList();
		LineRenderer renderer = new LineRenderer(context);
		renderers.addRenderer(renderer);

		int series = 0;
		for (ReportGraphData data : reportData) {
			dataset.add(data.getSeries());
			data.applySeriesStyle(series++, renderer);
			if (reportData.size() == 1) {
				renderer.setSeriesFillBelowLine(0, true);
			}

			if (isShowTrend()) {
				AbstractReportGraphData trendReportData = data
						.createRegressionData();
				dataset.add(trendReportData.getSeries());
				trendReportData.applySeriesStyle(series++, renderer);
			}
		}

		renderer.setOnClickListener(new OnClickListener() {
			@Override
			public void onSeriesClick(int series, int point) {
				Series s = dataset.get(series);
				String fuelType = s.getTitle() == null ? context
						.getString(R.string.report_toast_none) : s.getTitle();
				PointD p = s.get(point);
				String date = DateFormat.getDateFormat(context).format(
						new Date((long) p.x));
				Toast.makeText(
						context,
						String.format("%s: %s\n%s: %.3f %s\n%s: %s", context
								.getString(R.string.report_toast_fueltype),
								fuelType,
								context.getString(R.string.report_toast_price),
								p.y, unit, context
										.getString(R.string.report_toast_date),
								date), Toast.LENGTH_LONG).show();
			}
		});

		final Chart chart = new Chart(context, dataset, renderers);
		applyDefaultChartStyles(chart);
		chart.setShowLegend(false);
		chart.getDomainAxis().setLabelFormatter(dateLabelFormatter);
		chart.getRangeAxis()
				.setLabelFormatter(new DecimalAxisLabelFormatter(3));

		return chart;
	}

	@Override
	public int[] getGraphOptions() {
		return new int[1];
	}
}