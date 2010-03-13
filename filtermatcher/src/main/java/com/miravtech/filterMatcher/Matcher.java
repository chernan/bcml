package com.miravtech.filterMatcher;

import java.lang.reflect.Method;
import java.util.List;

import com.miravtech.sbgn.FindingType;
import com.miravtech.sbgn.SBGNGlyphType;



public class Matcher {
	
	public enum SearchResult {
		MATCH,
		NOMATCH,
		FIELDNOTFOUND
	};
	
	public static boolean contains(SBGNGlyphType g, FindingType f, String property, String val1) {
		SearchResult r = contains(f,property,val1);
		if (r == SearchResult.FIELDNOTFOUND) {
			// search the other places
			r = containsBean(g,property,val1);
			if (r == SearchResult.FIELDNOTFOUND &&  g != null) {
				r = containsBean(g.getGraphic(),property,val1);				
			}
		}
		return (r == SearchResult.MATCH);
	}

	/**
	 * Searches a bean. 
	 * 
	 * @param bean
	 * @param property
	 * @param val1
	 * @return
	 */
	public static SearchResult containsBean( Object bean, String property, String val1) {
		String value = val1.substring(1, val1.length() - 1);
		if (bean == null)
			return SearchResult.FIELDNOTFOUND; // keep looking, this field cannot give us a result.

		String prop = property.substring(0, 1).toUpperCase()
		+ property.substring(1);
		String method = "get" + prop;
		Method getter;
		try {
			getter = bean.getClass().getMethod(method);
		} catch (NoSuchMethodException m) {
			return SearchResult.FIELDNOTFOUND;
		}
		Object o;
		try {
			o = getter.invoke(bean);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		if (o == null)
			return SearchResult.NOMATCH; // null does not match anything
		if (value.compareTo(o.toString())==0){
			return SearchResult.MATCH;			
		} else {
			return SearchResult.NOMATCH;
		}
	}

	 
	/**
	 * 
	 * Searches a finding with the search algorithm.
	 * If the field is found, but unspecified (null), this is a match.
	 * 
	 * 
	 * @param f
	 * @param property
	 * @param val1
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static SearchResult contains( FindingType f, String property, String val1) {
		String value = val1.substring(1, val1.length() - 1);
		try {
			// System.out.println("Evaluating: " + property+"="+value);
			String prop = property.substring(0, 1).toUpperCase()
					+ property.substring(1);
			String method = "get" + prop;
			String enumType = "com.miravtech.sbgn." + prop + "Enum";
			Method getter;
			try {
				getter = FindingType.class.getMethod(method);
			} catch (NoSuchMethodException m) {
				return SearchResult.FIELDNOTFOUND;
			}
			Object o = getter.invoke(f);
			List l = (List) o;
			if (l.size() == 0)
				return SearchResult.MATCH;
			Class enumClass = Class.forName(enumType);
			Method getValue = enumClass.getMethod("value");
			for (Object o1 : l) {
				String val = (String) getValue.invoke(o1);
				if (val.equalsIgnoreCase(value))
					return SearchResult.MATCH;
			}
			return SearchResult.NOMATCH;
		} catch (Exception e) {
				throw new RuntimeException(e);
		}	
	}
}