package com.sforce.cd.apexUnit.model;

import java.util.Collection;

public class ToolingResponse {
	public Collection<ApexClass> records;
    public String entityTypeName;
    public String queryLocator;
    public boolean done;
    public int size;
    public int totalSize;
}