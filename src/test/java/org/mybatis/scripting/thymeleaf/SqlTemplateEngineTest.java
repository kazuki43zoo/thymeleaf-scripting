package org.mybatis.scripting.thymeleaf;

import java.util.*;

import org.junit.jupiter.api.Test;

class SqlTemplateEngineTest {

  @Test
  void test() {
    SqlTemplateEngine sqlTemplateEngine = new SqlTemplateEngine(ThymeleafLanguageDriverConfig.newInstance(c -> {
      c.getDialect().getNamedParameter().setPrefix(":");
      c.getDialect().getNamedParameter().setSuffix("");
    }));

    Param param = new Param();
    param.id = 1;
    Map<String, Object> map = new HashMap<>();
    String sql = sqlTemplateEngine.process(
        "SELECT * FROM test /*[# mb:bind='aaa=bbb,ccc=ddd' /]*/  /*[# th:if='${id} != null']*/ WHERE id = /*[# mb:p='id']*/ 1 /*[/]*/ /*[/]*/",
        param, map);
    System.out.println(sql);
    System.out.println(map);
    System.out.println(map.get("aaa"));
    System.out.println(map.get("ccc"));

  }

  @Test
  void test2() {
    SqlTemplateEngine sqlTemplateEngine = new SqlTemplateEngine(ThymeleafLanguageDriverConfig.newInstance(c -> {
      c.getDialect().getNamedParameter().setPrefix(":");
      c.getDialect().getNamedParameter().setSuffix("");
    }));

    // Param param = new Param();
    // param.id = 1;
    Map<String, Object> map = new HashMap<>();
    String sql = sqlTemplateEngine.process(
        "SELECT * FROM test /*[# mb:bind='id=bbb,ccc=ddd' /]*/ /*[# th:if='${id} != null']*/ WHERE id = /*[# mb:p='id']*/ 1 /*[/]*/ /*[/]*/",
        "2", map);
    System.out.println(sql);
    System.out.println(map);
    System.out.println(map.get("aaa"));
    System.out.println(map.get("ccc"));

  }

  @Test
  void test3() {
    SqlTemplateEngine sqlTemplateEngine = new SqlTemplateEngine(ThymeleafLanguageDriverConfig.newInstance(c -> {
      c.getDialect().getNamedParameter().setPrefix(":");
      c.getDialect().getNamedParameter().setSuffix("");
    }));

    Param param = new Param();
    param.ids = Arrays.asList(1, 2);
    Map<String, Object> map = new LinkedHashMap<>();
    // @formatter:off
    String sql = sqlTemplateEngine.process(
        "SELECT * FROM names\n" +
            "  WHERE 1 = 1\n" +
            "  /*[# th:if='${not #lists.isEmpty(ids)}']*/\n" +
            "    AND id IN (\n" +
            "    /*[# th:each='id : ${ids}']*/\n" +
            "      /*[# mb:bind='|ids[${idStat.index}]|=${ids[idStat.index]}' /]*/" +
            "      /*[# mb:p='ids[${idStat.index}]']*/ 1 /*[/]*/\n" +
            "      /*[(${idStat.last} ? '' : ',')]*/\n" +
            "    /*[/]*/\n" +
            "    )\n" +
            "  /*[/]*/\n" +
            "  AND text = /*[# mb:p='nestedParam.text']*/ 'bbbb' /*[/]*/" +
            "  ORDER BY id",
        param, map);
    // @formatter:on
    System.out.println(sql);
    System.out.println(map);
  }

  static class Param {
    private Integer id;
    private List<Integer> ids;
    private final NestedParam nestedParam = new NestedParam();
    private final List<NestedParam> nestedParamList = new ArrayList<>();

    public Integer getId() {
      return id;
    }

    public void setId(Integer id) {
      this.id = id;
    }

    public List<Integer> getIds() {
      return ids;
    }

    public void setIds(List<Integer> ids) {
      this.ids = ids;
    }

    public List<NestedParam> getNestedParamList() {
      return nestedParamList;
    }

    public NestedParam getNestedParam() {
      return nestedParam;
    }

    static class NestedParam {
      private String text;

      public String getText() {
        return text;
      }

      public void setText(String text) {
        this.text = text;
      }
    }
  }

}
