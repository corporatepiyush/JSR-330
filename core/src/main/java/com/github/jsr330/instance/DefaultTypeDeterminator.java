/**
 * Copyright 2012 the contributors
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.github.jsr330.instance;

import java.lang.annotation.Annotation;

import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jsr330.spi.TypeDeterminator;

/**
 * This TypeDeterminator chooses the candidate implementation using the annotations defined in JSR-330.
 */
public class DefaultTypeDeterminator implements TypeDeterminator {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTypeDeterminator.class);
    
    /**
     * Check for {@link Named} and tries to get the implementation named &lt;name&gt;&lt;typename&gt;.
     * E.g.: Tire will be instanced as SpareTire if named "spare".
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> Class<T> determineClass(Class<T> type, Class<? extends T>[] candidates, Annotation qualifier, ClassLoader classLoader) {
        String namePrefix = "";
        
        if (qualifier != null) {
            if (qualifier instanceof Named) {
                namePrefix = ((Named) qualifier).value();
            } else {
                namePrefix = qualifier.annotationType().getSimpleName();
            }
            
            if (candidates != null) {
                for (Class<? extends T> candidate : candidates) {
                    if (candidate.getSimpleName().toLowerCase().startsWith(namePrefix.toLowerCase())) {
                        LOGGER.debug("determineClass - returning decent {} for {}", candidate, type);
                        return (Class<T>) candidate;
                    }
                }
            }
        }
        
        LOGGER.debug("determineClass - returning {} for {}", type, type);
        return type;
    }
    
}
