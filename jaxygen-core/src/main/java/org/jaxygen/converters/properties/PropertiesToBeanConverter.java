/*
 * Copyright 2012 Artur Keska.
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
package org.jaxygen.converters.properties;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.beanutils.converters.*;
import org.jaxygen.converters.exceptions.DeserialisationError;
import org.jaxygen.converters.RequestConverter;
import org.jaxygen.network.UploadedFile;
import org.jaxygen.exceptions.MissingArgumentException;
import org.jaxygen.exceptions.WrongProperyIndex;
import org.jaxygen.http.HttpRequestParams;

/**
 *
 * @author Artur Keska
 */
public class PropertiesToBeanConverter implements RequestConverter {

 static final Map<Class<?>, Converter> converters = new HashMap<Class<?>, Converter>();

 static {
  converters.put(Boolean.class, new BooleanConverter());
  converters.put(Boolean.TYPE, new BooleanConverter());
  converters.put(Byte.class, new ByteConverter());
  converters.put(Byte.TYPE, new ByteConverter());
  converters.put(Character.class, new CharacterConverter());
  converters.put(Character.TYPE, new CharacterConverter());
  converters.put(Float.class, new FloatConverter());
  converters.put(Float.TYPE, new FloatConverter());
  converters.put(Integer.class, new IntegerConverter());
  converters.put(Integer.TYPE, new IntegerConverter());
  converters.put(Long.class, new LongConverter());
  converters.put(Long.TYPE, new LongConverter());
  converters.put(Short.class, new ShortConverter());
  converters.put(Short.TYPE, new ShortConverter());
  converters.put(Enum.class, new EnumConverter());
  converters.put(String.class, new StringConverter());
  for (Class<?> c : converters.keySet()) {
   ConvertUtils.register(converters.get(c), c);
  }
 }

 static public boolean isCovertable(Class<?> c) {
  return converters.containsKey(c);
 }
 public static final String NAME = "PROPERTIES";

 @Override
 public Object deserialise(HttpRequestParams params, Class<?> beanClass) throws DeserialisationError {
  try {
   return convertPropertiesToBean(params.getParameters(), params.getFiles(), beanClass);
  } catch (Exception ex) {
   throw new DeserialisationError("Could not parse input parameters for beed class " + beanClass, ex);
  }
 }

 /**
  * Applies a collection of properties to a JavaBean. Converts String and
  * String[] values to correct property types
  *
  * @param properties A map of the properties to set on the JavaBean
  * @param bean The JavaBean to set the properties on
  * @throws InstantiationException
  * @throws InvocationTargetException
  * @throws IllegalAccessException
  * @throws IntrospectionException
  * @throws IllegalArgumentException
  * @throws InstantiationException could not initialize instance of bean object.
  * @throws WrongProperyIndex
  * @throws MissingArgumentException One or more bean properties (annotated with
  * the MandatoryProperty annotation has not been found in the properties map.
  */
 public static Object convertPropertiesToBean(Map<String, String> properties,
         Map<String, UploadedFile> files,
         Class<?> beanClass) throws IllegalArgumentException,
         IntrospectionException, IllegalAccessException,
         InvocationTargetException, InstantiationException, WrongProperyIndex {
  Object bean = beanClass.newInstance();
  for (final String key : properties.keySet()) {
   final String value = properties.get(key);
   bean = fillBeanValueByName(key, value, beanClass, bean);
  }

  for (final String key : files.keySet()) {
   final UploadedFile value = files.get(key);
   bean = fillBeanValueByName(key, value, beanClass, bean);
  }

  return bean;
 }

 /**
  * Fill the field in bean by the value pointed by the name. Name format
  * name=<(KEY([N])?)+> where KEY bean property name, N index in table (if bean
  * field is List of java array).
  *
  * @param name
  * @param value
  * @param beanClass
  * @param baseBean
  * @param conversionReport
  * @return
  * @throws IntrospectionException
  * @throws IllegalArgumentException
  * @throws IllegalAccessException
  * @throws InvocationTargetException
  * @throws InstantiationException
  * @throws WrongProperyIndex
  */
 private static Object fillBeanValueByName(final String name, Object value,
         Class<?> beanClass, Object baseBean)
         throws IntrospectionException, IllegalArgumentException,
         IllegalAccessException, InvocationTargetException,
         InstantiationException, WrongProperyIndex {
  // parse name x.y[i].z[n].v
  Object bean = baseBean;
  if (bean == null) {
   bean = beanClass.newInstance();
  }
  Class<?> c = beanClass;
  BeanInfo beanInfo = Introspector.getBeanInfo(c, Object.class);
  final String childName = name.substring(name.indexOf(".") + 1);
  String path[] = name.split("\\.");

  final String fieldName = path[0];
  // parse arrays [n]
  if (fieldName.endsWith("]")) {
   int bracketStart = fieldName.indexOf("[");
   int len = fieldName.length();
   if (bracketStart > 0) {
    fillBeanArrayField(name, value, bean, beanInfo, path, fieldName,
            bracketStart, len);
   } else {
    throw new WrongProperyIndex(name);
   }
  } else {
   // parse non arrays
   for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
    if (pd.getName().equals(fieldName)) {
     Method writter = pd.getWriteMethod();
     Method reader = pd.getReadMethod();
     if (writter != null && reader != null) {
      Class<?> valueType = reader.getReturnType();
      if (path.length == 1) {
       Object valueObject = parsePropertyToValue(value, valueType);
       writter.invoke(bean, valueObject);
      } else {
       Object childBean = reader.invoke(bean);
       Object valueObject = fillBeanValueByName(childName, value,
               valueType, childBean);
       writter.invoke(bean, valueObject);
      }
     }
    }
   }
  }

  // Object bean = c.newInstance();

  return bean;
 }

 private static void fillBeanArrayField(final String name, Object value,
         Object bean, BeanInfo beanInfo, String[] path, final String fieldName,
         int bracketStart, int len)
         throws IllegalAccessException, InvocationTargetException,
         IntrospectionException, InstantiationException, IllegalArgumentException,
         WrongProperyIndex {
  final String indexStr = fieldName.substring(bracketStart + 1, len - 1);
  final String propertyName = fieldName.substring(0, bracketStart);
  int index = Integer.parseInt(indexStr);
  String childName = "";
  int firstDot = name.indexOf(".");
  if (firstDot > 0) {
   childName = name.substring(firstDot + 1);
  }

  for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
   if (pd.getName().equals(propertyName)) {
    Method writter = pd.getWriteMethod();
    Method reader = pd.getReadMethod();
    if (writter != null && reader != null) {
     Object array = reader.invoke(bean);
     if (pd.getPropertyType().isArray()) {
      if (array == null) {
       array = Array.newInstance(
               pd.getPropertyType().getComponentType(), index + 1);
       writter.invoke(bean, array);
      }
      if (Array.getLength(array) < (index + 1)) {
       array = resizeArray(array, index + 1);
       writter.invoke(bean, array);
      }
      if (path.length == 1) {
       Object valueObject = parsePropertyToValue(value, array.getClass().getComponentType());
       Array.set(array, index, valueObject);
      } else {
       Object valueObject = fillBeanValueByName(childName, value, array.getClass().getComponentType(), Array.get(array, index));
       Array.set(array, index, valueObject);
      }
     } else if (pd.getPropertyType().equals(List.class)) {
      if (array == null) {
       array = pd.getPropertyType().newInstance();
       writter.invoke(bean, array);
      }
      Class<?> genericClass = array.getClass().getTypeParameters()[0].getClass();
      if (path.length == 1) {
       Object valueObject = parsePropertyToValue(value, genericClass);
       Array.set(array, index, valueObject);
      } else {
       Object valueObject = fillBeanValueByName(childName, value,
               genericClass, null);
       Array.set(array, index, valueObject);
      }
     }
    }
   }
  }
 }

 private static Object parsePropertyToValue(Object valueObject,
         Class<?> propertyType) {
  Object value = null;

  //TODO: add cache of enum converters
  boolean isEnum = propertyType.isEnum();
  if (isEnum) {
   ConvertUtils.register(new EnumConverter(), propertyType);
  }

  if (valueObject.getClass().equals(String.class)) {
   value = ConvertUtils.convert((String) valueObject, propertyType);
  } else {
   value = valueObject;
  }

  return value;
 }

 private static Object resizeArray(Object array, int size) {
  Object newArray = Array.newInstance(array.getClass().getComponentType(),
          size);
  for (int i = 0; i < Array.getLength(array); i++) {
   Object value = Array.get(array, i);
   Array.set(newArray, i, value);
  }
  return newArray;
 }

 public String getName() {
  return NAME;
 }
}
