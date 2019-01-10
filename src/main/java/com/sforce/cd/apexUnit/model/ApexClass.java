package com.sforce.cd.apexUnit.model;

import java.util.Map;
import java.util.HashSet;

public class ApexClass {
	
	public String Name;
	public String Id;
	public SymbolTable SymbolTable;

	public boolean isTestClass() {
		return SymbolTable.tableDeclaration.modifiers.contains("testMethod");
	}
	
	public class SymbolTable {
		public String id;
		public String namespace;
		public String name;
		public Map<String, String> attributes;
		public TableDeclaration tableDeclaration;
		
		public class TableDeclaration {
			public HashSet<String> modifiers;
		}
	}

}
