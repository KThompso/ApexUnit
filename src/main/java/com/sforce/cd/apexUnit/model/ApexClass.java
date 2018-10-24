package com.sforce.cd.apexUnit.model;

import java.util.Map;

public class ApexClass {
	
	public String Name;
	public String Id;
	public SymbolTable SymbolTable;
	public boolean testClass = false;
	
	public class SymbolTable {
		public String id;
		public String namespace;
		public String name;
		public Map<String, String> attributes;
		public TableDeclaration tableDeclaration;
		
		public class TableDeclaration {
			public String[] modifiers;
		}
	}

}
