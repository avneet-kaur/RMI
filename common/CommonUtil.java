package common;

import java.util.Collection;
import java.util.Map;

public class CommonUtil {

	/**
	 * This method checks if the string is empty or not.
	 *
	 * @param str to check string
	 * @return boolean true if String is empty else false
	 */
	public static boolean isEmpty(String str) {
		return (str == null || str.trim().isEmpty());
	}

	/**
	 * This method checks if the string is non-empty or not.
	 *
	 * @param str1 to check string
	 * @return boolean true if string is not empty else false
	 */
	public static boolean isNotEmpty(String str1) {
		return !isEmpty(str1);
	}

	/**
	 * This method checks if the object is null or not.
	 *
	 * @param obj to check object
	 * @return true if object is null else false
	 */
	public static boolean isNull(Object obj) {
		return (obj == null);
	}

	/**
	 * This method checks if the collection is empty or not.
	 *
	 * @param collection to check collection
	 * @return true if collection is empty else false
	 */
	public static boolean isCollectionEmpty(Collection<?> collection) {
		return (collection == null || collection.isEmpty());
	}

	/**
	 * This method checks if the map is empty or not.
	 *
	 * @param map to check Map
	 * @return true if map is empty else false
	 */
	public static boolean isMapEmpty(Map<?, ?> map) {
		return (map == null || map.isEmpty());
	}

}
