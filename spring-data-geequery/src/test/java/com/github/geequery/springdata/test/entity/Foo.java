package com.github.geequery.springdata.test.entity;

import java.util.Date;
import javax.persistence.Entity;

@Entity
public class Foo extends jef.database.DataObject {
	private static final long serialVersionUID = 1L;

	private int id;

	private String name;

	private String remark;

	private Date birthDay;
	
	private int age;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public Date getBirthDay() {
		return birthDay;
	}

	public void setBirthDay(Date birthDay) {
		this.birthDay = birthDay;
	}

	public int getAge() {
		return age;
	}

	public void setAge(int age) {
		this.age = age;
	}

	public enum Field implements jef.database.Field {
		id, name, remark, birthDay,age
	}
}
