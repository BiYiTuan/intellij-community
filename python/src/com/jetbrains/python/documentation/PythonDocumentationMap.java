// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author yole
 */
@State(name = "PythonDocumentationMap", storages = @Storage("other.xml"))
public class PythonDocumentationMap implements PersistentStateComponent<PythonDocumentationMap.State> {

  public static final String PYQT4_DOC_URL =
    "http://pyqt.sourceforge.net/Docs/PyQt4/{class.name.lower}.html#{function.name}";

  public static final String PYQT4_DOC_URL_OLD =
    "http://www.riverbankcomputing.co.uk/static/Docs/PyQt4/html/{class.name.lower}.html#{function.name}";
  public static final String PyQt4 = "PyQt4";

  public static PythonDocumentationMap getInstance() {
    return ServiceManager.getService(PythonDocumentationMap.class);
  }

  public static class Entry {
    private String myPrefix;
    private String myUrlPattern;

    public Entry() {
    }

    public Entry(String prefix, String urlPattern) {
      myPrefix = prefix;
      myUrlPattern = urlPattern;
    }

    public String getPrefix() {
      return myPrefix;
    }

    public String getUrlPattern() {
      return myUrlPattern;
    }

    public void setPrefix(String prefix) {
      myPrefix = prefix;
    }

    public void setUrlPattern(String urlPattern) {
      myUrlPattern = urlPattern;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Entry entry = (Entry)o;

      if (!myPrefix.equals(entry.myPrefix)) return false;
      if (!myUrlPattern.equals(entry.myUrlPattern)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myPrefix.hashCode();
      result = 31 * result + myUrlPattern.hashCode();
      return result;
    }
  }

  public static class State {
    private List<Entry> myEntries = new ArrayList<>();

    public State() {
      addEntry(PyQt4, PYQT4_DOC_URL);
      addEntry("PyQt5", "http://doc.qt.io/qt-5/{class.name.lower}.html#{functionOrProp.name}");
      addEntry("PySide", "http://pyside.github.io/docs/pyside/{module.name.slashes}/{class.name}.html#{module.name}.{element.qname}");
      addEntry("gtk",
               "http://library.gnome.org/devel/pygtk/stable/class-gtk{class.name.lower}.html#method-gtk{class.name.lower}--{function.name.dashes}");
      addEntry("wx", "http://www.wxpython.org/docs/api/{module.name}.{class.name}-class.html#{function.name}");
      addEntry("kivy", "http://kivy.org/docs/api-{module.name}.html");
      addEntry("matplotlib", "http://matplotlib.org/api/{module.basename}_api.html#{element.qname}");
      addEntry("pyramid", "http://docs.pylonsproject.org/projects/pyramid/en/latest/api/{module.basename}.html#{element.qname}");
      addEntry("flask", "http://flask.pocoo.org/docs/latest/api/#{element.qname}");
    }

    public List<Entry> getEntries() {
      return myEntries;
    }

    public void setEntries(List<Entry> entries) {
      myEntries = entries;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      State state = (State)o;
      return Sets.newHashSet(myEntries).equals(Sets.newHashSet(state.getEntries()));
    }

    @Override
    public int hashCode() {
      return myEntries != null ? myEntries.hashCode() : 0;
    }

    private void addEntry(String qName, String pattern) {
      myEntries.add(new Entry(qName, pattern));
    }
  }

  private State myState = new State();

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
    for (Entry e : myState.getEntries()) {
      if (PyQt4.equals(e.myPrefix) && PYQT4_DOC_URL_OLD.equals(e.myUrlPattern)) {
        // old URL is broken, switch to new one
        e.setUrlPattern(PYQT4_DOC_URL);
      }
    }
    addAbsentEntriesFromDefaultState(myState);
    removeEntriesThatHandledSpecially(myState);
  }

  private static void removeEntriesThatHandledSpecially(@NotNull State state) {
    ArrayList<String> strings = Lists.newArrayList("django", "numpy", "scipy");
    // those packages are handled by implementations of PythonDocumentationLinkProvider
    state.setEntries(state.getEntries().stream().filter((entry -> !strings.contains(entry.myPrefix))).collect(Collectors.toList()));
  }

  private static void addAbsentEntriesFromDefaultState(@NotNull State state) {
    State defaultState = new State();
    for (Entry e : defaultState.myEntries) {
      if (state.myEntries.stream().noneMatch(entry -> entry.myPrefix.equals(e.myPrefix))) {
        state.addEntry(e.getPrefix(), e.getUrlPattern());
      }
    }
  }

  public List<Entry> getEntries() {
    return ImmutableList.copyOf(myState.getEntries());
  }

  public void setEntries(List<Entry> entries) {
    myState.setEntries(entries);
  }

  @Nullable
  public String urlFor(QualifiedName moduleQName, @Nullable PsiNamedElement element, String pyVersion) {
    for (Entry entry : myState.myEntries) {
      if (moduleQName.matchesPrefix(QualifiedName.fromDottedString(entry.myPrefix))) {
        return transformPattern(entry.myUrlPattern, moduleQName, element, pyVersion);
      }
    }
    return null;
  }

  private static String rootForPattern(String urlPattern) {
    int pos = urlPattern.indexOf('{');
    return pos >= 0 ? urlPattern.substring(0, pos) : urlPattern;
  }

  @Nullable
  private static String transformPattern(@NotNull String urlPattern, QualifiedName moduleQName, @Nullable PsiNamedElement element,
                                         String pyVersion) {
    Map<String, String> macros = new HashMap<>();
    macros.put("element.name", element == null ? null : element.getName());
    PyClass pyClass = element == null ? null : PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    macros.put("class.name", pyClass == null ? null : pyClass.getName());
    if (element != null) {
      StringBuilder qName = new StringBuilder(moduleQName.toString()).append(".");
      if (element instanceof PyFunction && ((PyFunction)element).getContainingClass() != null) {
        qName.append(((PyFunction)element).getContainingClass().getName()).append(".");
      }
      qName.append(element.getName());
      macros.put("element.qname", qName.toString());
    }
    else {
      macros.put("element.qname", "");
    }
    macros.put("function.name", element instanceof PyFunction ? element.getName() : "");
    macros.put("functionOrProp.name", element instanceof PyFunction && element.getName() != null ? functionOrProp(element.getName()) : "");
    macros.put("module.name", moduleQName.toString());
    macros.put("python.version", pyVersion);
    macros.put("module.basename", moduleQName.getLastComponent());
    final String pattern = transformPattern(urlPattern, macros);
    if (pattern == null) {
      return rootForPattern(urlPattern);
    }
    return pattern;
  }

  private static String functionOrProp(@NotNull String name) {
    String functionOrProp = StringUtil.getPropertyName(name);
    if (!name.equals(functionOrProp)) {
      functionOrProp += functionOrProp + "-prop";
    }
    return functionOrProp;
  }

  @Nullable
  private static String transformPattern(@NotNull String urlPattern, Map<String, String> macroValues) {
    for (Map.Entry<String, String> entry : macroValues.entrySet()) {
      if (entry.getValue() == null) {
        if (urlPattern.contains("{" + entry.getKey())) {
          return null;
        }
        continue;
      }
      urlPattern = urlPattern
        .replace("{" + entry.getKey() + "}", entry.getValue())
        .replace("{" + entry.getKey() + ".lower}", entry.getValue().toLowerCase())
        .replace("{" + entry.getKey() + ".slashes}", entry.getValue().replace(".", "/"))
        .replace("{" + entry.getKey() + ".dashes}", entry.getValue().replace("_", "-"));
    }
    return urlPattern.replace("{}", "");
  }
}
