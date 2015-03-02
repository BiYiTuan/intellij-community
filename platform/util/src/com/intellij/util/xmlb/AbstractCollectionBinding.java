/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.xmlb;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import gnu.trove.THashMap;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

abstract class AbstractCollectionBinding extends Binding implements MultiNodeBinding, MainBinding {
  private Map<Class<?>, Binding> itemBindings;

  protected final Class<?> itemType;
  private final AbstractCollection annotation;

  public AbstractCollectionBinding(@NotNull Class elementType, @Nullable Accessor accessor) {
    super(accessor);

    itemType = elementType;
    annotation = accessor == null ? null : accessor.getAnnotation(AbstractCollection.class);
  }

  @Override
  public boolean isMulti() {
    return true;
  }

  @Override
  public void init(@NotNull Type originalType) {
    if (annotation == null || annotation.surroundWithTag()) {
      return;
    }

    if (StringUtil.isEmpty(annotation.elementTag()) ||
        (annotation.elementTag().equals(Constants.OPTION) && XmlSerializerImpl.getBinding(itemType) == null)) {
      throw new XmlSerializationException("If surround with tag is turned off, element tag must be specified for: " + myAccessor);
    }
  }

  @NotNull
  private synchronized Map<Class<?>, Binding> getElementBindings() {
    if (itemBindings == null) {
      Binding binding = XmlSerializerImpl.getBinding(itemType);
      if (annotation == null || annotation.elementTypes().length == 0) {
        itemBindings = binding == null ? Collections.<Class<?>, Binding>emptyMap() : Collections.<Class<?>, Binding>singletonMap(itemType, binding);
      }
      else {
        itemBindings = new THashMap<Class<?>, Binding>();
        if (binding != null) {
          itemBindings.put(itemType, binding);
        }
        for (Class aClass : annotation.elementTypes()) {
          Binding b = XmlSerializerImpl.getBinding(aClass);
          if (b != null) {
            itemBindings.put(aClass, b);
          }
        }
        if (itemBindings.isEmpty()) {
          itemBindings = Collections.emptyMap();
        }
      }
    }
    return itemBindings;
  }

  @Nullable
  private Binding getElementBinding(@NotNull Object node) {
    for (Binding binding : getElementBindings().values()) {
      if (binding.isBoundTo(node)) {
        return binding;
      }
    }
    return null;
  }

  abstract Object processResult(Collection result, Object target);

  @NotNull
  abstract Collection<Object> getIterable(@NotNull Object o);

  @Nullable
  @Override
  public Object serialize(@NotNull Object o, @Nullable Object context, @NotNull SerializationFilter filter) {
    Collection<Object> collection = getIterable(o);

    String tagName = getTagName(o);
    if (tagName == null) {
      List<Object> result = new SmartList<Object>();
      if (!ContainerUtil.isEmpty(collection)) {
        for (Object item : collection) {
          ContainerUtil.addAllNotNull(result, serializeItem(item, result, filter));
        }
      }
      return result;
    }
    else {
      Element result = new Element(tagName);
      if (!ContainerUtil.isEmpty(collection)) {
        for (Object item : collection) {
          Content child = (Content)serializeItem(item, result, filter);
          if (child != null) {
            result.addContent(child);
          }
        }
      }
      return result;
    }
  }

  @Nullable
  @Override
  public Object deserializeList(Object context, @NotNull List<?> nodes) {
    Collection result;
    if (getTagName(context) == null) {
      if (context instanceof Collection) {
        result = (Collection)context;
        result.clear();
      }
      else {
        result = new SmartList();
      }
      for (Object node : nodes) {
        if (!XmlSerializerImpl.isIgnoredNode(node)) {
          //noinspection unchecked
          result.add(deserializeItem(node, context));
        }
      }

      if (result == context) {
        return result;
      }
    }
    else {
      assert nodes.size() == 1;
      result = deserializeSingle(context, (Element)nodes.get(0));
    }
    return processResult(result, context);
  }


  @Nullable
  private Object serializeItem(@Nullable Object value, Object context, @NotNull SerializationFilter filter) {
    if (value == null) {
      throw new XmlSerializationException("Collection " + myAccessor + " contains 'null' object");
    }

    Binding binding = XmlSerializerImpl.getBinding(value.getClass());
    if (binding == null) {
      Element serializedItem = new Element(annotation == null ? Constants.OPTION : annotation.elementTag());
      String attributeName = annotation == null ? Constants.VALUE : annotation.elementValueAttribute();
      if (attributeName.isEmpty()) {
        serializedItem.addContent(new Text(XmlSerializerImpl.convertToString(value)));
      }
      else {
        serializedItem.setAttribute(attributeName, XmlSerializerImpl.convertToString(value));
      }
      return serializedItem;
    }
    else {
      return binding.serialize(value, context, filter);
    }
  }

  private Object deserializeItem(Object node, Object context) {
    Binding binding = getElementBinding(node);
    if (binding == null) {
      String attributeName = annotation == null ? Constants.VALUE : annotation.elementValueAttribute();
      String value;
      if (attributeName.isEmpty()) {
        List<Content> children = ((Element)node).getContent();
        if (children.isEmpty()) {
          value = null;
        }
        else {
          Content content = children.get(0);
          value = content instanceof Text ? content.getValue() : null;
        }
      }
      else {
        value = ((Element)node).getAttributeValue(attributeName);
      }
      return XmlSerializerImpl.convert(value, itemType);
    }
    else {
      return binding.deserialize(context, node);
    }
  }

  @Override
  public Object deserialize(Object context, @NotNull Object node) {
    Collection result;
    if (getTagName(context) == null) {
      if (context instanceof Collection) {
        result = (Collection)context;
        result.clear();
      }
      else {
        result = new SmartList();
      }

      if (!XmlSerializerImpl.isIgnoredNode(node)) {
        //noinspection unchecked
        result.add(deserializeItem(node, context));
      }

      if (result == context) {
        return result;
      }
    }
    else {
      result = deserializeSingle(context, (Element)node);
    }
    return processResult(result, context);
  }

  @NotNull
  private Collection deserializeSingle(Object context, @NotNull Element node) {
    Collection result = createCollection(node.getName());
    for (Content child : node.getContent()) {
      if (!XmlSerializerImpl.isIgnoredNode(child)) {
        //noinspection unchecked
        result.add(deserializeItem(child, context));
      }
    }
    return result;
  }

  protected Collection createCollection(@NotNull String tagName) {
    return new SmartList();
  }

  @Override
  public boolean isBoundTo(Object node) {
    if (!(node instanceof Element)) {
      return false;
    }

    Element element = (Element)node;
    String tagName = getTagName(node);
    if (tagName == null) {
      if (element.getName().equals(annotation == null ? Constants.OPTION : annotation.elementTag())) {
        return true;
      }

      if (getElementBinding(node) != null) {
        return true;
      }
    }

    return element.getName().equals(tagName);
  }

  @Nullable
  private String getTagName(@Nullable Object target) {
    return annotation == null || annotation.surroundWithTag() ? getCollectionTagName(target) : null;
  }

  protected abstract String getCollectionTagName(@Nullable Object target);
}
