// =====================================================================================
// requisitions-list.js: the Approve buttons on the requisitions list page
//
// Loaded by requisitions.jsp, after jQuery.
//
// WHAT HAPPENS WHEN YOU CLICK APPROVE
//   1. Ask "are you sure?" (approving creates a purchase order)
//   2. Send a background request: POST /mini-procurement/requisitions/approve  id=11
//      This is AJAX: the request happens without leaving or reloading the page.
//   3. ApproveRequisitionServlet approves it in the database and replies with JSON:
//        {"reqId":11,"status":"APPROVED","poId":7}
//   4. Update just that one row: the status turns green, and the button disappears.
//      If the server reports a problem, show its message instead.
//
// Updating one row is faster and smoother than reloading the whole page, and the user
// keeps their scroll position.
// =====================================================================================

// Runs once the page's HTML is ready (short for $(document).ready(...))
$(function () {
  // Find the table once and keep it in a variable
  const $table = $('#requisitions-table');

  // .data('approve-url') reads the table's data-approve-url="..." attribute. The JSP
  // built that URL on the server (/mini-procurement/requisitions/approve), so this file
  // doesn't need to know the app's path.
  const approveUrl = $table.data('approve-url');

  // ---------------------------------------------------------------------------------
  // EVENT DELEGATION
  // Instead of attaching a click handler to every Approve button, attach ONE handler to
  // the table. When a click happens anywhere inside it, jQuery checks whether it was on
  // something matching '.approve-btn', and if so runs the handler. Fewer handlers, and
  // it also works for buttons added to the table later.
  // ---------------------------------------------------------------------------------
  $table.on('click', '.approve-btn', function () {
    const $button = $(this);                // the button that was clicked
    const reqId = $button.data('id');       // its data-id="..." value, such as 11
    const $row = $button.closest('tr');     // the nearest <tr> above it: its table row

    // window.confirm shows a dialog with OK and Cancel. It returns true for OK and
    // false for Cancel. On Cancel, "return" stops the handler, so nothing is sent.
    if (!window.confirm('Approve requisition ' + reqId + ' and create a purchase order?')) {
      return;
    }

    // Disable the button while waiting, so a double-click can't send two requests.
    // .prop() sets a live property of the element. For on/off states like
    // disabled or checked, .prop() is the right method rather than .attr().
    $button.prop('disabled', true).text('Approving...');

    // ---------------------------------------------------------------------------------
    // The AJAX request. $.ajax takes an object of settings:
    //   url        where to send it
    //   method     the HTTP method; POST because approving changes data
    //   data       the values to send. jQuery encodes { id: 11 } as "id=11", just like
    //              a submitted form field, so the servlet reads it with
    //              request.getParameter("id").
    //   dataType   what we expect back. 'json' tells jQuery to parse the reply text
    //              into a JavaScript object for us.
    //
    // $.ajax returns right away; the reply arrives later (it's ASYNCHRONOUS). We say
    // what to do when it arrives by chaining:
    //   .done(...)   runs if the reply's status is 2xx (success)
    //   .fail(...)   runs for 4xx and 5xx replies, or if the server can't be reached
    // (Modern JavaScript can do the same thing without jQuery, using fetch().)
    // ---------------------------------------------------------------------------------
    $.ajax({
      url: approveUrl,
      method: 'POST',
      data: { id: reqId },
      dataType: 'json'
    })
      .done(function (result) {
        // result is the parsed JSON: result.reqId, result.status, result.poId
        // Update the status cell: swap the color class and the text
        $row.find('td.status')
          .removeClass('pending')
          .addClass('approved')
          .text(result.status);
        // It's approved now, so the button isn't needed
        $button.remove();
        showMessage('Requisition ' + result.reqId + ' approved. Purchase order ' + result.poId + ' created.', 'msg-ok');
      })
      .fail(function (xhr) {
        // xhr describes the failed request. For the errors the servlet expects (400,
        // 409) it sends JSON like {"error":"..."}, which jQuery has parsed into
        // xhr.responseJSON. For anything else (a 500 error page, or the server being
        // down) there's no responseJSON.
        //
        // "a && a.error" only reads .error if a exists (otherwise it would crash on
        // undefined). "x || 'default'" uses x if it has a value, otherwise the default.
        const message = (xhr.responseJSON && xhr.responseJSON.error)
          || 'Something went wrong. Please try again.';
        showMessage(message, 'msg-error');
        // Put the button back so the user can try again
        $button.prop('disabled', false).text('Approve');
      });
  });

  // Shows a message in the <p id="message"> above the table.
  //   cssClass   'msg-ok' (green) or 'msg-error' (red); see styles.css
  // .text() inserts plain text, never HTML, so a message can't inject markup.
  // removeClass first, so a green message never keeps an old red class (or vice versa).
  function showMessage(text, cssClass) {
    $('#message').text(text).removeClass('msg-ok msg-error').addClass(cssClass);
  }
});
