package dhbw.sose2022.softwareengineering.airportagentsim.simulation.config;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.commons.lang3.Validate;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import dhbw.sose2022.softwareengineering.airportagentsim.simulation.api.config.ConfigurableAttribute;
import dhbw.sose2022.softwareengineering.airportagentsim.simulation.api.config.ConfigurationFormatException;
import dhbw.sose2022.softwareengineering.airportagentsim.simulation.api.config.ConfigurationParseException;
import dhbw.sose2022.softwareengineering.airportagentsim.simulation.api.simulation.entity.Entity;

public final class ConfigurationTypeRegistry {
	
	private final HashMap<Class<?>, RegistryEntry> entries = new HashMap<Class<?>, RegistryEntry>();
	
	private final HashMap<String, Class<? extends Entity>> entitiesByID = new HashMap<String, Class<? extends Entity>>();
	
	
	public ConfigurationTypeRegistry() {
		registerSimpleEntry(Boolean.class);
		registerSimpleEntry(Byte.class);
		registerSimpleEntry(Short.class);
		registerSimpleEntry(Integer.class);
		registerSimpleEntry(Long.class);
		registerSimpleEntry(Float.class);
		registerSimpleEntry(Double.class);
	}
	
	@SuppressWarnings("unchecked")
	public <T> T parseJSONObject(Class<T> type, JSONObject object) throws ConfigurationFormatException, ConfigurationParseException {
		
		if(type.isArray())
			throw new ConfigurationParseException("Cannot parse JSON object to array");
		
		RegistryEntry registryEntry = this.entries.get(type);
		if(registryEntry == null || !(registryEntry instanceof ObjectRegistryEntry))
			throw new ConfigurationFormatException("No configuration format defined for " + type.getSimpleName());
		
		Object o = parseObject((ObjectRegistryEntry) registryEntry, object);
		if(!type.isInstance(o))
			throw new ConfigurationFormatException("Invalid configuration format for " + type.getSimpleName());
		return (T) o;
		
	}
	
	@SuppressWarnings("unchecked")
	public <T> T parseJSONArray(Class<T> type, JSONArray array) throws ConfigurationFormatException, ConfigurationParseException {
		
		if(!type.isArray())
			throw new ConfigurationParseException("Cannot parse JSON array to non-array object");
		
		Object o = parseArray(type, array);
		if(!type.isInstance(o))
			throw new ConfigurationFormatException("Invalid configuration format for " + type.getSimpleName());
		return (T) o;
		
	}
	
	public void registerConfigurationType(Class<?> type, ConfigurableAttribute[] parameters) throws ConfigurationFormatException {
		
		Validate.notNull(type);
		Validate.noNullElements(parameters);
		Validate.isTrue(!type.isArray(), "Cannot define array configuration types explicitly");
		
		if(parameters.length > 0) {
			HashSet<String> configKeys = new HashSet<String>();
			for(int i = 0; i < parameters.length; i++)
				if(!configKeys.add(parameters[i].getConfigurationKey()))
					throw new ConfigurationFormatException("Cannot define a configuration type which defines multiple parameters using the same configuration key");
		}
		
		ObjectRegistryEntry ore = new ObjectRegistryEntry(type, parameters);
		if(this.entries.containsKey(type)) {
			if(ore.equals(this.entries.get(type)))
				return;
			throw new ConfigurationFormatException("Cannot redefine configuration type " + type.getSimpleName());
		}
		this.entries.put(type, ore);
		
	}
	
	public boolean isEntityIDRegistered(String entityID) {
		return this.entitiesByID.containsKey(entityID);
	}
	
	public Entity parseEntity(String entityID, JSONObject object) throws ConfigurationFormatException, ConfigurationParseException {
		Class<? extends Entity> entityType = this.entitiesByID.get(entityID);
		if(entityType == null)
			throw new ConfigurationFormatException("Unknown entity ID \"" + entityID + "\"");
		return parseJSONObject(entityType, object);
	}
	
	public void registerEntityID(String entityID, Class<? extends Entity> type) throws IllegalArgumentException {
		if(this.entitiesByID.containsKey(entityID))
			throw new IllegalArgumentException("Duplicate entity id: " + entityID);
		this.entitiesByID.put(entityID, type);
	}
	
	
	private void registerSimpleEntry(Class<?> type) {
		this.entries.put(type, new SimpleRegistryEntry(type));
	}
	
	private Object parseObject(ObjectRegistryEntry registryEntry, JSONObject object) throws ConfigurationFormatException, ConfigurationParseException {
		
		Object[] parameters = new Object[registryEntry.parameters.length];
		
		HashSet<Integer> undefinedIndecies = new HashSet<Integer>();
		for(int i = 0; i < parameters.length; i++)
			undefinedIndecies.add(i);
		
		for(Object keyObject : object.keySet()) {
			
			String key = String.valueOf(keyObject);
			Integer parameterIndex = registryEntry.parametersByName.get(key);
			if(parameterIndex == null)
				throw new ConfigurationParseException("Attempting to parse an object of type " + registryEntry.target.getSimpleName() + ", but found illegal configuration key \"" + key + "\"");
			
			ConfigurableAttribute childAttribute = registryEntry.parameters[parameterIndex];
			try {
				parameters[parameterIndex] = parseJSONElement(childAttribute.getType(), object.get(keyObject));
			} catch(ConfigurationParseException e) {
				throw new ConfigurationParseException("Failed to parse an object of type " + registryEntry.target.getSimpleName() + ": " + e.getMessage());
			}
			
			undefinedIndecies.remove(parameterIndex);
			
		}
		
		for(Integer undefinedIndex : undefinedIndecies) {
			if(registryEntry.parameters[undefinedIndex].isRequired())
				throw new ConfigurationParseException("Attempting to parse an object of type " + registryEntry.target.getSimpleName() + ", but required configuration key \"" + registryEntry.parameters[undefinedIndex].getConfigurationKey() + "\" is missing");
			parameters[undefinedIndex] = registryEntry.parameters[undefinedIndex].getDefaultValue();
		}
		
		return registryEntry.parse(parameters);
		
	}
	
	private Object parseArray(Class<?> targetType, JSONArray array) throws ConfigurationFormatException, ConfigurationParseException {
		
		Class<?> componentType = targetType.getComponentType();
		
		int length = array.size();
		Object resultArray = Array.newInstance(componentType, length);
		
		for(int i = 0; i < length; i++) {
			try {
				Array.set(resultArray, i, parseJSONElement(componentType, array.get(i)));
			} catch(ConfigurationParseException e) {
				throw new ConfigurationParseException("Failed to parse an array of type " + targetType.getSimpleName() + ": Error at index " + i + ": " + e.getMessage());
			}
		}
		
		return resultArray;
		
	}
	
	private Object parseJSONElement(Class<?> targetType, Object element) throws ConfigurationFormatException, ConfigurationParseException {
		
		if(targetType.isArray()) {
			if(!(element instanceof JSONArray))
				throw new ConfigurationParseException("Failed to parse " + targetType.getSimpleName() + ". Expected " + JSONArray.class.getSimpleName() + ", got " + element.getClass().getSimpleName());
			return parseArray(targetType, (JSONArray) element);
		}
		
		RegistryEntry registryEntry = this.entries.get(targetType);
		
		if(registryEntry instanceof SimpleRegistryEntry) {
			
			SimpleRegistryEntry sre = (SimpleRegistryEntry) registryEntry;
			
			if(Number.class.isAssignableFrom(sre.target)) {
				
				if(!(element instanceof Number))
					throw new ConfigurationParseException("Expected " + targetType.getSimpleName() + ", got " + element.getClass().getSimpleName());
				
				Number converted = parseNumber((Number) element, sre.target);
				if(converted != null)
					return converted;
				
			}
			
			if(!sre.target.isInstance(element))
				throw new ConfigurationParseException("Expected " + targetType.getSimpleName() + ", got " + element.getClass().getSimpleName());
			
			return element;
			
		} else if(registryEntry instanceof ObjectRegistryEntry) {
			
			if(!(element instanceof JSONObject))
				throw new ConfigurationParseException("Failed to parse " + targetType.getSimpleName() + ". Expected " + JSONObject.class.getSimpleName() + ", got " + element.getClass().getSimpleName());
			
			return parseObject((ObjectRegistryEntry) registryEntry, (JSONObject) element);
			
		} else {
			throw new ConfigurationFormatException("No configuration type definition for type " + targetType.getSimpleName());
		}
		
	}
	
	private Number parseNumber(Number original, Class<?> target) throws ConfigurationFormatException, ConfigurationParseException {
		
		if(original.getClass() == target)
			return original;
		
		if(target == Double.class)
			return Double.valueOf(original.doubleValue());
		if(target == Float.class)
			return Float.valueOf(original.floatValue());
		
		if(!(original instanceof Long) && !(original instanceof Integer) && !(original instanceof Short) && !(original instanceof Byte))
			throw new ConfigurationParseException("Attempting to parse an object of type " + target.getSimpleName() + ", but configuration value is not an integer");
		
		Number converted;
		if(target == Byte.class) {
			converted = Byte.valueOf(original.byteValue());
		} else if(target == Short.class) {
			converted = Short.valueOf(original.shortValue());
		} else if(target == Integer.class) {
			converted = Integer.valueOf(original.intValue());
		} else if(target == Long.class) {
			converted = Long.valueOf(original.longValue());
		} else {
			return null;
		}
		
		if(converted.longValue() != original.longValue())
			throw new ConfigurationParseException("Attempting to parse an object of type " + target.getSimpleName() + ", but configuration value is out of range");
		
		return converted;
		
	}
	
}
