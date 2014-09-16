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
package org.jaxygen.converters;

import org.jaxygen.converters.exceptions.DeserialisationError;
import org.jaxygen.http.HttpRequestParams;

/**Generic class for request processing.
 * Resposibility of this clsass is to converte request data (whatever it is)
 * into java POJO.
 *
 * It needs to be guaranteed by the implementation of this class that the deserialize oprtation
 * is thread save.
 * @author Artur Keska
 */
public interface RequestConverter {
  String getName();
  Object deserialise(HttpRequestParams params, Class<?> beanClass) throws DeserialisationError;
}
