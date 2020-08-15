document.addEventListener('DOMContentLoaded', function () {
    $(function () {
        $('[data-toggle="tooltip"]').tooltip()
    });

    document.getElementById('modalOkBtn').addEventListener('click', approveOrReject);
}, false);

function toggleLoader(e) {
    $('#loader').prop("hidden", false);
}

url = "";
data = {};
reqId = -1;

function sendRequest(toggleButtons) {
    posting = $.ajax({
        url: url,
        type: "POST",
        data: JSON.stringify(data),
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        success: function (data, textStatus) {
            if (data.reload) {
                location.reload()
            }
            toggleButtons(false);
        },
        error: function (data) {
            alert("request failed: " + data.responseJSON.message)
            toggleButtons(false);
        }
    });
}

function approveOrReject() {
    function toggleButtons(status = true) {
        $('#approveLoader-' + reqId).prop("hidden", !status);
        $('#approveBtn-' + reqId).prop("disabled", status);
        $('#rejectBtn-' + reqId).prop("disabled", status);
    }

    toggleButtons();
    $("#propModal").modal("toggle");
    sendRequest(toggleButtons)
}

function approveProp(id, memberId) {
    document.getElementById('modalTitle').innerHTML = "Approval";
    document.getElementById('modalBody').innerHTML = "Are you sure you want to approve this proposal?";
    url = "/proposal/" + id + "/approve";
    data = {"memberId": memberId};
    reqId = id;
    $("#propModal").modal("show")
}

function rejectProp(id, memberId) {
    document.getElementById('modalTitle').innerHTML = "Rejection";
    document.getElementById('modalBody').innerHTML = "Are you sure you want to reject this proposal?";
    url = "/proposal/" + id + "/reject";
    data = {"memberId": memberId};
    reqId = id;
    $("#propModal").modal("show")
}

function openDecisionModal(id, memberId, reqName) {
    document.getElementById('decisionTitle').innerHTML = reqName + " Final Decision";
    reqId = id;
    $("#decisionModal").modal("show")
}

function decideProp() {
    function toggleButtons(status = true) {
        console.log(reqId);
        $('#decisionLoader-' + reqId).prop("hidden", !status);
        $('#decisionBtn-' + reqId).prop("disabled", status);
    };

    url = "/proposal/" + reqId + "/decide";
    data = {"decision": document.getElementById("exampleRadios1").checked};
    toggleButtons();
    $("#decisionModal").modal("toggle");
    sendRequest(toggleButtons)
}
