package com.yammer.dropwizard.views.freemarker;

import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.sun.jersey.api.container.ContainerException;
import com.yammer.dropwizard.views.View;
import com.yammer.dropwizard.views.ViewRenderer;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.mozilla.universalchardet.UniversalDetector;

import javax.ws.rs.WebApplicationException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Locale;

public class FreemarkerViewRenderer implements ViewRenderer {
    private static class ViewClass {
        private final Class<?> klass;
        private final View view;

        public ViewClass(Class<?> klass, View view) {
            this.klass = klass;
            this.view = view;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ViewClass viewClass = (ViewClass) o;

            if (!klass.equals(viewClass.klass)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return klass.hashCode();
        }
    }
    private static class TemplateLoader extends CacheLoader<ViewClass, Configuration> {
        @Override
        public Configuration load(ViewClass viewClass) throws Exception {
            final Configuration configuration = new Configuration();
            configuration.setObjectWrapper(new DefaultObjectWrapper());
            String templateName = viewClass.view.getTemplateName();
            String encoding = detectViewEncoding(templateName);
            if (encoding == null) {
                configuration.loadBuiltInEncodingMap();
                configuration.setDefaultEncoding(Charsets.UTF_8.name());
            } else {
                configuration.setDefaultEncoding(encoding);
            }
            configuration.setClassForTemplateLoading(viewClass.klass, "/");
            return configuration;
        }

        private static String detectViewEncoding(String viewName) {
            String encoding;

            try {
                byte[] buf = new byte[4096];
                java.io.FileInputStream fis = new java.io.FileInputStream(viewName);
                UniversalDetector detector = new UniversalDetector(null);
                int nread;
                while ((nread = fis.read(buf)) > 0 && !detector.isDone()) {
                    detector.handleData(buf, 0, nread);
                }
                detector.dataEnd();
                encoding = detector.getDetectedCharset();
            } catch (IOException e) {
                encoding = null;
            }

            return encoding;
        }
    }

    private final LoadingCache<ViewClass, Configuration> configurationCache;

    public FreemarkerViewRenderer() {
        this.configurationCache = CacheBuilder.newBuilder()
                                              .concurrencyLevel(128)
                                              .build(new TemplateLoader());
    }

    @Override
    public boolean isRenderable(View view) {
        return view.getTemplateName().endsWith(".ftl");
    }

    @Override
    public void render(View view,
                       Locale locale,
                       OutputStream output) throws IOException, WebApplicationException {
        try {
            ViewClass viewClass = new ViewClass(view.getClass(), view);
            final Configuration configuration = configurationCache.getUnchecked(viewClass);
            final Template template = configuration.getTemplate(view.getTemplateName(), locale);
            template.process(view, new OutputStreamWriter(output, template.getEncoding()));
        } catch (TemplateException e) {
            throw new ContainerException(e);
        }
    }

}
