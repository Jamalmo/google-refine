package com.google.gridworks.commands.project;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gridworks.ProjectManager;
import com.google.gridworks.ProjectMetadata;
import com.google.gridworks.commands.Command;
import com.google.gridworks.model.Project;
import com.google.gridworks.util.ParsingUtilities;

public class ImportProjectCommand extends Command {

    final static Logger logger = LoggerFactory.getLogger("import-project_command");

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        ProjectManager.singleton.setBusy(true);
        try {
            Properties options = ParsingUtilities.parseUrlParameters(request);

            long projectID = Project.generateID();
            logger.info("Importing existing project using new ID {}", projectID);

            internalImport(request, options, projectID);

            ProjectManager.singleton.loadProjectMetadata(projectID);

            ProjectMetadata pm = ProjectManager.singleton.getProjectMetadata(projectID);
            if (pm != null) {
                if (options.containsKey("project-name")) {
                    String projectName = options.getProperty("project-name");
                    if (projectName != null && projectName.length() > 0) {
                        pm.setName(projectName);
                    }
                }

                redirect(response, "/project?project=" + projectID);
            } else {
                redirect(response, "/error.html?redirect=index.html&msg=" +
                    ParsingUtilities.encode("Failed to import project")
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ProjectManager.singleton.setBusy(false);
        }
    }

    protected void internalImport(
        HttpServletRequest    request,
        Properties            options,
        long                  projectID
    ) throws Exception {

        String url = null;

        ServletFileUpload upload = new ServletFileUpload();

        FileItemIterator iter = upload.getItemIterator(request);
        while (iter.hasNext()) {
            FileItemStream item = iter.next();
            String name = item.getFieldName().toLowerCase();
            InputStream stream = item.openStream();
            if (item.isFormField()) {
                if (name.equals("url")) {
                    url = Streams.asString(stream);
                } else {
                    options.put(name, Streams.asString(stream));
                }
            } else {
                String fileName = item.getName().toLowerCase();
                try {
                    ProjectManager.singleton.importProject(projectID, stream, !fileName.endsWith(".tar"));
                } finally {
                    stream.close();
                }
            }
        }

        if (url != null && url.length() > 0) {
            internalImportURL(request, options, projectID, url);
        }
    }

    protected void internalImportURL(
        HttpServletRequest    request,
        Properties            options,
        long                  projectID,
        String                urlString
    ) throws Exception {
        URL url = new URL(urlString);
        URLConnection connection = null;

        try {
            connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.connect();
        } catch (Exception e) {
            throw new Exception("Cannot connect to " + urlString, e);
        }

        InputStream inputStream = null;
        try {
            inputStream = connection.getInputStream();
        } catch (Exception e) {
            throw new Exception("Cannot retrieve content from " + url, e);
        }

        try {
            ProjectManager.singleton.importProject(projectID, inputStream, !urlString.endsWith(".tar"));
        } finally {
            inputStream.close();
        }
    }


}