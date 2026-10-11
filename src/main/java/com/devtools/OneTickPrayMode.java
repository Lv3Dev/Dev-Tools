package com.devtools;

public enum OneTickPrayMode
{
	HOLD("Hold"),
	TOGGLE("Toggle");

	private final String name;

	OneTickPrayMode(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
