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

package me.kuehle.carreport.gui;

import me.kuehle.carreport.Preferences;
import me.kuehle.carreport.R;
import me.kuehle.carreport.reports.AbstractReport;
import me.kuehle.carreport.reports.AbstractReport.CalculationOption;
import me.kuehle.carreport.reports.CostsReport;
import me.kuehle.carreport.reports.FuelConsumptionReport;
import me.kuehle.carreport.reports.FuelPriceReport;
import me.kuehle.carreport.util.SectionListAdapter;
import me.kuehle.carreport.util.WeightAnimator;
import me.kuehle.chartlib.ChartView;
import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

public class ReportActivity extends Activity implements OnMenuItemClickListener {
	private static final int ADD_REFUELING_REQUEST_CODE = 0;
	private static final int ADD_OTHER_REQUEST_CODE = 1;
	private static final int VIEW_DATA_REQUEST_CODE = 2;
	private static final int PREFERENCES_REQUEST_CODE = 3;

	private AbstractReport mCurrentReport;
	private int mCurrentGraphOption;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_report);

		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
				this, R.array.reports,
				android.R.layout.simple_spinner_dropdown_item);

		ActionBar actionBar = getActionBar();
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		actionBar.setListNavigationCallbacks(adapter, navigationListener);

		Object reportId = getLastNonConfigurationInstance();
		if (reportId != null) {
			actionBar.setSelectedNavigationItem((Integer) reportId);
		} else {
			Preferences prefs = new Preferences(this);
			actionBar.setSelectedNavigationItem(prefs.getDefaultReport());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.report, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_add_refueling:
			Intent intent = new Intent(this, DataDetailActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
			intent.putExtra(DataDetailActivity.EXTRA_EDIT,
					DataDetailActivity.EXTRA_EDIT_REFUELING);
			startActivityForResult(intent, ADD_REFUELING_REQUEST_CODE);
			return true;
		case R.id.menu_add_other:
			Intent intent3 = new Intent(this, DataDetailActivity.class);
			intent3.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
			intent3.putExtra(DataDetailActivity.EXTRA_EDIT,
					DataDetailActivity.EXTRA_EDIT_OTHER);
			startActivityForResult(intent3, ADD_OTHER_REQUEST_CODE);
			return true;
		case R.id.menu_calculate:
			startActionMode(mCalculationActionMode);
			return true;
		case R.id.menu_view_data:
			Intent intent1 = new Intent(this, DataListActivity.class);
			startActivityForResult(intent1, VIEW_DATA_REQUEST_CODE);
			return true;
		case R.id.menu_settings:
			Intent intent2 = new Intent(this, PreferencesActivity.class);
			startActivityForResult(intent2, PREFERENCES_REQUEST_CODE);
			return true;
		case R.id.menu_help:
			Intent intent4 = new Intent(this, HelpActivity.class);
			startActivity(intent4);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if ((requestCode == ADD_REFUELING_REQUEST_CODE && resultCode == RESULT_OK)
				|| (requestCode == ADD_OTHER_REQUEST_CODE && resultCode == RESULT_OK)
				|| requestCode == VIEW_DATA_REQUEST_CODE
				|| requestCode == PREFERENCES_REQUEST_CODE) {
			updateReport();
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		ActionBar actionBar = getActionBar();
		return actionBar.getSelectedNavigationIndex();
	}

	@Override
	public boolean onMenuItemClick(MenuItem item) {
		if (item.getItemId() == R.id.menu_show_trend) {
			mCurrentReport.setShowTrend(!item.isChecked());
			updateReportGraph();
			return true;
		} else if (item.getGroupId() == R.id.group_graph) {
			mCurrentGraphOption = item.getOrder();
			updateReportGraph();
			return true;
		} else {
			return false;
		}
	}

	public void showReportOptions(View v) {
		PopupMenu popup = new PopupMenu(this, v);
		popup.inflate(R.menu.report_options);
		popup.setOnMenuItemClickListener(this);

		Menu menu = popup.getMenu();
		MenuItem item = menu.findItem(R.id.menu_show_trend);
		item.setChecked(mCurrentReport.isShowTrend());

		int[] graphOptions = mCurrentReport.getGraphOptions();
		if (graphOptions.length >= 2) {
			for (int i = 0; i < graphOptions.length; i++) {
				item = menu
						.add(R.id.group_graph, Menu.NONE, i, graphOptions[i]);
				item.setChecked(i == mCurrentGraphOption);
			}
			menu.setGroupCheckable(R.id.group_graph, true, true);
		}

		popup.show();
	}

	private void updateReport() {
		ActionBar actionBar = getActionBar();
		int reportIndex = actionBar.getSelectedNavigationIndex();

		switch (reportIndex) {
		case 0:
			mCurrentReport = new FuelConsumptionReport(getApplicationContext());
			break;
		case 1:
			mCurrentReport = new FuelPriceReport(getApplicationContext());
			break;
		case 2:
			mCurrentReport = new CostsReport(getApplicationContext());
			break;
		default:
			return;
		}
		mCurrentGraphOption = 0;

		updateReportGraph();

		ListView lstData = (ListView) findViewById(R.id.lstData);
		Preferences prefs = new Preferences(ReportActivity.this);
		lstData.setAdapter(new SectionListAdapter(this, mCurrentReport
				.getData().getData(), prefs.isColorSections()));
	}

	private void updateReportGraph() {
		FrameLayout graphHolder = (FrameLayout) findViewById(R.id.graph_holder);
		ChartView graph = (ChartView) findViewById(R.id.graph);
		graph.setChart(mCurrentReport.getChart(mCurrentGraphOption));
		if (graph.getChart() == null) {
			graphHolder.setVisibility(View.GONE);
		} else {
			graphHolder.setVisibility(View.VISIBLE);
		}
	}

	private OnNavigationListener navigationListener = new OnNavigationListener() {
		@Override
		public boolean onNavigationItemSelected(int itemPosition, long itemId) {
			updateReport();
			return true;
		}
	};

	private ActionMode.Callback mCalculationActionMode = new ActionMode.Callback() {
		private WeightAnimator graphAnim;
		private EditText input;
		private int option;

		@Override
		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mCurrentReport.getData().resetCalculation();

			InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);

			graphAnim.expand(null, null);
		}

		@Override
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			CalculationOption[] options = mCurrentReport
					.getCalculationOptions();
			if (options.length == 0) {
				return false;
			} else if (options.length > 1) {
				for (int i = 0; i < options.length; i++) {
					MenuItem item = menu.add(Menu.NONE, Menu.NONE, i,
							options[i].getName());
					item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
				}
			}
			option = 0;

			View graph = ReportActivity.this.findViewById(R.id.graph_holder);
			graphAnim = new WeightAnimator(graph, 500);

			input = new EditText(ReportActivity.this);
			input.setInputType(InputType.TYPE_CLASS_NUMBER
					| InputType.TYPE_NUMBER_FLAG_DECIMAL);
			input.setLines(1);
			input.setHint(options[option].getHint1());
			input.addTextChangedListener(new TextWatcher() {
				@Override
				public void onTextChanged(CharSequence s, int start,
						int before, int count) {
				}

				@Override
				public void beforeTextChanged(CharSequence s, int start,
						int count, int after) {
				}

				@Override
				public void afterTextChanged(Editable s) {
					applyCalculation(s.toString());
				}
			});
			mode.setCustomView(input);
			input.requestFocus();

			graphAnim.collapse(null, new Runnable() {
				@Override
				public void run() {
					InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					keyboard.showSoftInput(input, 0);
				}
			});

			applyCalculation(input.getText().toString());
			return true;
		}

		@Override
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			option = item.getOrder();
			input.setHint(mCurrentReport.getCalculationOptions()[option]
					.getHint1());
			applyCalculation(input.getText().toString());
			return true;
		}

		private void applyCalculation(String input) {
			double value1 = 1;
			try {
				value1 = Double.parseDouble(input);
			} catch (NumberFormatException e) {
			}
			mCurrentReport.getData().applyCalculation(value1, option);
			((ListView) ReportActivity.this.findViewById(R.id.lstData))
					.invalidateViews();
		}
	};
}