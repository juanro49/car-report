package me.kuehle.carreport.db;

import java.util.ArrayList;
import java.util.Date;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class Refueling extends AbstractItem {
	private Date date;
	private int tachometer;
	private float volume;
	private float price;
	private boolean partial;
	private String note;
	private Car car;

	public Refueling(int id) {
		Helper helper = Helper.getInstance();
		SQLiteDatabase db = helper.getReadableDatabase();
		Cursor cursor = db.query(RefuelingTable.NAME, null,
				RefuelingTable.COL_ID + "=?",
				new String[] { String.valueOf(id) }, null, null, null);
		if (cursor.getCount() != 1) {
			cursor.close();
			throw new IllegalArgumentException(
					"A fuel with this ID does not exist!");
		} else {
			cursor.moveToFirst();
			this.id = id;
			this.date = new Date(cursor.getLong(1));
			this.tachometer = cursor.getInt(2);
			this.volume = cursor.getFloat(3);
			this.price = cursor.getFloat(4);
			this.partial = cursor.getInt(5) > 0;
			this.note = cursor.getString(6);
			this.car = new Car(cursor.getInt(7));
			cursor.close();
		}
	}

	private Refueling(int id, Date date, int tachometer, float volume,
			float price, boolean partial, String note, Car car) {
		this.id = id;
		this.date = date;
		this.tachometer = tachometer;
		this.volume = volume;
		this.price = price;
		this.partial = partial;
		this.note = note;
		this.car = car;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
		save();
	}

	public int getTachometer() {
		return tachometer;
	}

	public void setTachometer(int tachometer) {
		this.tachometer = tachometer;
		save();
	}

	public float getVolume() {
		return volume;
	}

	public void setVolume(float volume) {
		this.volume = volume;
		save();
	}

	public float getPrice() {
		return price;
	}

	public void setPrice(float price) {
		this.price = price;
		save();
	}

	public boolean isPartial() {
		return partial;
	}

	public void setPartial(boolean partial) {
		this.partial = partial;
		save();
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
		save();
	}

	public Car getCar() {
		return car;
	}

	public void setCar(Car car) {
		this.car = car;
		save();
	}

	public void delete() {
		if (!isDeleted()) {
			Helper helper = Helper.getInstance();
			SQLiteDatabase db = helper.getWritableDatabase();
			db.delete(RefuelingTable.NAME, RefuelingTable.COL_ID + "=?",
					new String[] { String.valueOf(id) });
			deleted = true;
		}
	}

	private void save() {
		if (!isDeleted()) {
			Helper helper = Helper.getInstance();
			SQLiteDatabase db = helper.getWritableDatabase();

			ContentValues values = new ContentValues();
			values.put(RefuelingTable.COL_DATE, date.getTime());
			values.put(RefuelingTable.COL_TACHO, tachometer);
			values.put(RefuelingTable.COL_VOLUME, volume);
			values.put(RefuelingTable.COL_PRICE, price);
			values.put(RefuelingTable.COL_PARTIAL, partial);
			values.put(RefuelingTable.COL_NOTE, note);
			values.put(RefuelingTable.COL_CAR, car.getId());
			db.update(RefuelingTable.NAME, values,
					RefuelingTable.COL_ID + "=?",
					new String[] { String.valueOf(id) });
		}
	}

	public static Refueling create(Date date, int tachometer, float volume,
			float price, boolean partial, String note, Car car) {
		Helper helper = Helper.getInstance();
		SQLiteDatabase db = helper.getWritableDatabase();

		ContentValues values = new ContentValues();
		values.put(RefuelingTable.COL_DATE, date.getTime());
		values.put(RefuelingTable.COL_TACHO, tachometer);
		values.put(RefuelingTable.COL_VOLUME, volume);
		values.put(RefuelingTable.COL_PRICE, price);
		values.put(RefuelingTable.COL_PARTIAL, partial);
		values.put(RefuelingTable.COL_NOTE, note);
		values.put(RefuelingTable.COL_CAR, car.getId());
		int id = (int) db.insert(RefuelingTable.NAME, null, values);

		return new Refueling(id, date, tachometer, volume, price, partial,
				note, car);
	}

	public static Refueling[] getAllForCar(Car car, boolean orderDateAsc) {
		ArrayList<Refueling> refuelings = new ArrayList<Refueling>();

		Helper helper = Helper.getInstance();
		SQLiteDatabase db = helper.getReadableDatabase();
		Cursor cursor = db.query(RefuelingTable.NAME, null,
				RefuelingTable.COL_CAR + "=?", new String[] { String
						.valueOf(car.getId()) }, null, null, String.format(
						"%s %s", RefuelingTable.COL_DATE, orderDateAsc ? "ASC"
								: "DESC"));

		cursor.moveToFirst();
		while (!cursor.isAfterLast()) {
			refuelings.add(new Refueling(cursor.getInt(0), new Date(cursor
					.getLong(1)), cursor.getInt(2), cursor.getFloat(3), cursor
					.getFloat(4), cursor.getInt(5) > 0, cursor.getString(6),
					car));
			cursor.moveToNext();
		}
		cursor.close();

		return refuelings.toArray(new Refueling[refuelings.size()]);
	}

	public static int getCount() {
		Helper helper = Helper.getInstance();
		SQLiteDatabase db = helper.getReadableDatabase();
		Cursor cursor = db.rawQuery("SELECT count(*) FROM "
				+ RefuelingTable.NAME, null);
		cursor.moveToFirst();
		int count = cursor.getInt(0);
		cursor.close();
		return count;
	}

	public static Refueling getFirst() {
		Helper helper = Helper.getInstance();
		SQLiteDatabase db = helper.getReadableDatabase();
		Cursor cursor = db.rawQuery("SELECT * FROM " + RefuelingTable.NAME
				+ " ORDER BY " + RefuelingTable.COL_DATE + " LIMIT 1", null);

		if (cursor.getCount() != 1) {
			return null;
		}
		cursor.moveToFirst();
		Refueling fuel = new Refueling(cursor.getInt(0), new Date(
				cursor.getLong(1)), cursor.getInt(2), cursor.getFloat(3),
				cursor.getFloat(4), cursor.getInt(5) > 0, cursor.getString(6),
				new Car(cursor.getInt(7)));
		cursor.close();

		return fuel;
	}

	public static Refueling getLast() {
		Helper helper = Helper.getInstance();
		SQLiteDatabase db = helper.getReadableDatabase();
		Cursor cursor = db.rawQuery("SELECT * FROM " + RefuelingTable.NAME
				+ " ORDER BY " + RefuelingTable.COL_DATE + " DESC LIMIT 1",
				null);

		if (cursor.getCount() != 1) {
			return null;
		}
		cursor.moveToFirst();
		Refueling fuel = new Refueling(cursor.getInt(0), new Date(
				cursor.getLong(1)), cursor.getInt(2), cursor.getFloat(3),
				cursor.getFloat(4), cursor.getInt(5) > 0, cursor.getString(6),
				new Car(cursor.getInt(7)));
		cursor.close();

		return fuel;
	}
}
