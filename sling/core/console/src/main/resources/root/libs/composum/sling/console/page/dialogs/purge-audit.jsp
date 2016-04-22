<%@page session="false" pageEncoding="utf-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<div id="purge-audit-dialog" class="dialog modal fade" role="dialog" aria-hidden="true">
    <div class="modal-dialog">
        <div class="modal-content form-panel default">

            <form class="widget-form">

                <div class="modal-header">
                    <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span
                            aria-hidden="true">&times;</span></button>
                    <h4 class="modal-title">Purge Audit Log</h4>
                </div>
                <div class="modal-body">
                    <div class="messages">
                        <div class="alert"></div>
                    </div>
                    <input name="_charset_" type="hidden" value="UTF-8"/>
                    <input name="path" type="hidden" value="${pckg.path}">

                    <div class="form-group">
                        <label class="control-label">Topic</label>
                        <input name="event.job.topic" class="widget text-field-widget form-control" type="text"
                               placeholder="Job topic" data-rules="mandatory"/>
                    </div>
                    <div class="form-group">
                        <label class="control-label">Reference</label>
                        <input name="reference" class="widget text-field-widget form-control" type="text"
                               placeholder="enter group name (path)" data-rules="mandatory"/>
                    </div>
                    <div class="form-group">
                        <label class="control-label">Keep <span>number of audit entries to keep</span></label>
                        <input name="keep" class="widget number-field-widget form-control" type="text"
                               autofocus data-rules="mandatory"/>
                    </div>
                </div>

                <div class="modal-footer buttons">
                    <button type="button" class="btn btn-default cancel" data-dismiss="modal">Cancel</button>
                    <button type="submit" class="btn btn-primary purge">Purge</button>
                </div>

            </form>
        </div>
    </div>
</div>