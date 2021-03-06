package com.birdbraintechnologies.birdblox.httpservice;

import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/**
 * Interface for anything that can handle requests from the HttpService
 *
 * @author Terence Sun (tsun1215)
 */
public interface RequestHandler {

    /**
     * Handles a request
     *
     * @param session HttpSession generated
     * @param args    List of arguments generated by matching regex groups
     * @return Response to the request
     */
    NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args);
}
