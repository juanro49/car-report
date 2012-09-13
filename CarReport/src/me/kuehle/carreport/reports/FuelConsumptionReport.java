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

package me.kuehle.carreport.reports;

import java.util.Date;
import java.util.Vector;

import me.kuehle.carreport.Preferences;
import me.kuehle.carreport.R;
import me.kuehle.carreport.db.Car;
import me.kuehle.carreport.db.Refueling;
import me.kuehle.carreport.util.Calculator;
import me.kuehle.carreport.util.SectionListAdapter.Item;
import me.kuehle.carreport.util.SectionListAdapter.Section;
import me.kuehle.chartlib.axis.DecimalAxisLabelFormatter;
import me.kuehle.chartlib.chart.Chart;
import me.kuehle.chartlib.data.Dataset;
import me.kuehle.chartlib.data.Series;
import me.kuehle.chartlib.renderer.LineRenderer;
import me.kuehle.chartlib.renderer.OnClickListener;
import me.kuehle.chartlib.renderer.RendererList;
import android.content.Context;
import android.text.format.DateFormat;
import android.widget.Toast;

public class FuelConsumptionReport extends AbstractReport {
	private class CalculableItem extends ReportData.AbstractCalculableItem {
		private static final String FORMAT = "%.2f %s";
		private double value;
		private String[] calcLabels;

		public CalculableItem(String label, double value) {
			this(label, value, new String[] { label, label });
		}

		public CalculableItem(String label, double value, String[] calcLabels) {
			super(label, String.format(FORMAT, value, unit));
			this.value = value;
			this.calcLabels = calcLabels;
		}

		@Override
		public void applyCalculation(double value1, int option) {
			Preferences prefs = new Preferences(context);
			if (option == 0) {
				double result = value1 / (value / 100);
				setValue(String.format(FORMAT, result, prefs.getUnitDistance()));
			} else if (option == 1) {
				double result = (value / 100) * value1;
				setValue(String.format(FORMAT, result, prefs.getUnitVolume()));
			}
			setLabel(calcLabels[option]);
		}
	}

	private class ReportGraphData extends AbstractReportGraphData {
		public ReportGraphData(Context context, Car car) {
			super(context, car.getName(), car.getColor());

			Refueling[] refuelings = Refueling.getAllForCar(car, true);
			int lastTacho = -1;
			float volume = 0;
			for (Refueling refueling : refuelings) {
				volume += refueling.getVolume();
				if (!refueling.isPartial()) {
					if (lastTacho > -1) {
						double consumption = volume
								/ (refueling.getMileage() - lastTacho) * 100;
						xValues.add(refueling.getDate().getTime());
						yValues.add(consumption);
					}
					lastTacho = refueling.getMileage();
					volume = 0;
				}
			}
		}
	}

	private Vector<AbstractReportGraphData> reportData = new Vector<AbstractReportGraphData>();

	private String unit;

	private boolean showLegend;

	public FuelConsumptionReport(Context context) {
		super(context);

		// Preferences
		Preferences prefs = new Preferences(context);
		unit = String.format("%s/100%s", prefs.getUnitVolume(),
				prefs.getUnitDistance());
		showLegend = prefs.isShowLegend();

		// Collect report data and add info data which will be displayed
		// next to the graph.
		Vector<Double> consumptions = new Vector<Double>();
		Car[] cars = Car.getAll();
		for (Car car : cars) {
			ReportGraphData carData = new ReportGraphData(context, car);
			Section section = addDataSection(car.getName(), car.getColor());

			if (carData.size() == 0) {
				section.addItem(new Item(context
						.getString(R.string.report_not_enough_data), ""));
			} else {
				reportData.add(carData);

				consumptions.addAll(carData.yValues);
				addConsumptionData(section, carData.yValues);
			}
		}

		// Only display overall section, when at least report data for 2
		// cars is present.
		if (reportData.size() >= 2) {
			addConsumptionData(addDataOverallSection(), consumptions);
		}
	}

	private void addConsumptionData(Section section, Vector<Double> numbers) {
		section.addItem(new CalculableItem(context
				.getString(R.string.report_highest), Calculator.max(numbers),
				new String[] { context.getString(R.string.report_at_least),
						context.getString(R.string.report_at_most) }));
		section.addItem(new CalculableItem(context
				.getString(R.string.report_lowest), Calculator.min(numbers),
				new String[] { context.getString(R.string.report_at_most),
						context.getString(R.string.report_at_least) }));
		section.addItem(new CalculableItem(context
				.getString(R.string.report_average), Calculator.avg(numbers)));
	}

	@Override
	public CalculationOption[] getCalculationOptions() {
		Preferences prefs = new Preferences(context);
		return new CalculationOption[] {
				new CalculationOption(context.getString(
						R.string.report_calc_vol2mileage_name,
						prefs.getUnitVolume()), prefs.getUnitVolume()),
				new CalculationOption(context.getString(
						R.string.report_calc_mileage2vol_name,
						prefs.getUnitVolume(), prefs.getUnitDistance()),
						prefs.getUnitDistance()) };
	}

	@Override
	public Chart getChart(int option) {
		final Dataset dataset = new Dataset();
		RendererList renderers = new RendererList();
		LineRenderer renderer = new LineRenderer(context);
		renderers.addRenderer(renderer);

		Vector<AbstractReportGraphData> reportData = new Vector<AbstractReportGraphData>();
		if (isShowTrend()) {
			for (AbstractReportGraphData data : this.reportData) {
				reportData.add(data.createRegressionData());
			}
		}
		reportData.addAll(this.reportData);
		for (int i = 0; i < reportData.size(); i++) {
			dataset.add(reportData.get(i).getSeries());
			reportData.get(i).applySeriesStyle(i, renderer);
		}

		renderer.setOnClickListener(new OnClickListener() {
			@Override
			public void onSeriesClick(int series, int point) {
				Series s = dataset.get(series);
				String car = s.getTitle();
				String date = DateFormat.getDateFormat(context).format(
						new Date((long) s.get(point).x));
				Toast.makeText(
						context,
						String.format(
								"%s: %s\n%s: %.2f %s\n%s: %s",
								context.getString(R.string.report_toast_car),
								car,
								context.getString(R.string.report_toast_consumption),
								s.get(point).y, unit, context
										.getString(R.string.report_toast_date),
								date), Toast.LENGTH_LONG).show();
			}
		});

		final Chart chart = new Chart(context, dataset, renderers);
		applyDefaultChartStyles(chart);
		chart.setShowLegend(showLegend);
		if (isShowTrend()) {
			for (int i = 0; i < reportData.size() / 2; i++) {
				chart.getLegend().setSeriesVisible(i, false);
			}
		}
		chart.getDomainAxis().setLabelFormatter(dateLabelFormatter);
		chart.getRangeAxis()
				.setLabelFormatter(new DecimalAxisLabelFormatter(2));

		return chart;
	}

	@Override
	public int[] getGraphOptions() {
		return new int[1];
	}
}
