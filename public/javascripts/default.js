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
function approveOrReject() {
    function toggleButtons(status = true) {
        $('#approveLoader').prop("hidden", !status);
        $('#approveBtn').prop("disabled", status);
        $('#rejectBtn').prop("disabled", status);
    }

    toggleButtons();
    $("#propModal").modal("toggle");
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

function approveProp(id, memberId) {
    document.getElementById('modalTitle').innerHTML = "Approval";
    document.getElementById('modalBody').innerHTML = "Are you sure you want to approve this proposal?";
    url = "/proposal/" + id + "/approve";
    data = {"memberId": memberId};
    $("#propModal").modal("show")
}

function rejectProp(id, memberId) {
    document.getElementById('modalTitle').innerHTML = "Rejection";
    document.getElementById('modalBody').innerHTML = "Are you sure you want to reject this proposal?";
    url = "/proposal/" + id + "/reject";
    data = {"memberId": memberId};
    $("#propModal").modal("show")
}
