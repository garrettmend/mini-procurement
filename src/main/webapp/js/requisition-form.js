// =====================================================================================
// requisition-form.js: checks the New Requisition form in the browser
//
// Loaded by requisition.html, after jQuery. When the user clicks Submit, this checks
// every field. If anything is wrong, it marks the field red, shows a message under it,
// and stops the form from being sent.
//
// CLIENT-SIDE vs SERVER-SIDE VALIDATION
// This check is only for the user's convenience: instant feedback without a round trip
// to the server. It is NOT security. Anyone can skip it by turning off JavaScript or by
// sending the request with a tool like curl. That's why RequisitionServlet.doPost()
// checks everything again on the server. The rules here mirror the server's rules.
//
// JQUERY IN 30 SECONDS
//   $                    the jQuery function (jQuery is a library that makes working
//                        with the page shorter and more consistent across browsers)
//   $('#item')           finds elements using CSS selector syntax: #id, .class, tag
//   $('#item').val()     calls a jQuery method on what was found (here: read its value)
//   $form                a NAMING HABIT, not syntax: a $ at the start of a variable name
//                        reminds you it holds a jQuery object, not a plain value
//   .a().b().c()         most jQuery methods return the same object, so calls chain
// =====================================================================================

// $(function () { ... }) is short for $(document).ready(function () { ... }).
// The code inside runs once the page's HTML has been fully read, so every element we
// look up below already exists. (This script is at the end of the body, so they would
// exist anyway, but using ready is the standard, safe habit.)
$(function () {
  // Find the form once and keep it. "const" means this variable can't be reassigned.
  const $form = $('#requisition-form');

  // ---------------------------------------------------------------------------------
  // Run the checks when the form is submitted.
  // .on('submit', handler) calls the handler function every time the form is about to
  // be sent. "event" is an object describing what happened; we use it to cancel the
  // submit if something is wrong.
  // ---------------------------------------------------------------------------------
  $form.on('submit', function (event) {
    // Remove messages left over from the previous attempt
    clearErrors();

    // Read each field. .val() returns the current value as a string; .trim() removes
    // spaces at both ends, so "   " counts as empty.
    const requester = $('#requester').val().trim();
    const item = $('#item').val().trim();
    const quantity = $('#quantity').val().trim();
    const unitCost = $('#unit_cost').val().trim();
    const vendorId = $('#vendor_id').val();   // the selected option's value, "" if none

    // Required, and no longer than the database column allows. || means OR, and
    // === means "equal, with the same type" (JavaScript's == converts types first,
    // which causes surprises, so === is preferred).
    if (requester === '' || requester.length > 100) {
      showError('#requester', 'Enter a requester (100 characters max).');
    }
    if (item === '' || item.length > 200) {
      showError('#item', 'Enter an item (200 characters max).');
    }

    // REGULAR EXPRESSIONS (patterns that text must match), written between / /
    //   /^\d+$/                 ^ = start of the text, \d = a digit, + = one or more,
    //                           $ = end of the text. So: digits only, like "20".
    //   /^\d+(\.\d{1,2})?$/     digits, then OPTIONALLY (the ?) a dot followed by 1 or 2
    //                           digits. So "12", "12.5" and "12.50" match; "12.345",
    //                           "-5" and "abc" don't. (\. is a literal dot; a bare . means
    //                           "any character".)
    // .test(text) returns true if the text matches the pattern.
    // parseInt(quantity, 10) turns "20" into the number 20 (10 = base ten).
    if (!/^\d+$/.test(quantity) || parseInt(quantity, 10) < 1) {
      showError('#quantity', 'Enter a whole number of at least 1.');
    }
    if (!/^\d+(\.\d{1,2})?$/.test(unitCost)) {
      showError('#unit_cost', 'Enter a cost like 12 or 12.50 (no more than 2 decimals).');
    }
    if (vendorId === '') {
      showError('#vendor_id', 'Choose a vendor.');
    }

    // showError() added the class "invalid" to every bad field. .find() looks inside
    // the form for those fields; .length is how many were found.
    const $invalid = $form.find('.invalid');
    if ($invalid.length > 0) {
      // Cancel the submit, so nothing is sent to the server
      event.preventDefault();
      // Put the cursor in the first field that needs fixing
      $invalid.first().trigger('focus');
    }
    // If nothing was invalid, we do nothing and the browser submits the form normally.
  });

  // ---------------------------------------------------------------------------------
  // Clear a field's error as soon as the user starts fixing it.
  //
  // This is EVENT DELEGATION: one handler on the form, with the filter 'input, select',
  // handles events from every input and select inside the form.
  //   'input change'   two events: "input" fires on every keystroke in a text box,
  //                    "change" fires when a dropdown choice changes
  //   this             inside a handler, the element the event happened on
  //   $(this)          wraps that element in jQuery so we can call jQuery methods
  //   .next('.error-message')
  //                    the element right after this field, if it's an error message
  // ---------------------------------------------------------------------------------
  $form.on('input change', 'input, select', function () {
    $(this).removeClass('invalid').next('.error-message').remove();
  });

  // ---------------------------------------------------------------------------------
  // Helper functions.
  // They're used above but defined down here. That works because JavaScript "hoists"
  // function declarations: they exist from the start of their surrounding function.
  // (Hoisting is a common interview question.)
  // ---------------------------------------------------------------------------------

  // Marks one field as invalid and puts a message right after it.
  //   selector   a CSS selector for the field, like '#quantity'
  //   message    the text to show
  function showError(selector, message) {
    const $field = $(selector);
    // "invalid" makes the border red (see styles.css), and lets the submit handler
    // count the bad fields
    $field.addClass('invalid');
    // $('<div ...></div>') CREATES a new element (instead of finding one).
    // .text(message) sets its content as PLAIN TEXT. .html(message) would treat the
    // message as HTML, which is dangerous if the text ever came from a user (XSS).
    // .insertAfter($field) places the new element right after the field.
    $('<div class="error-message"></div>').text(message).insertAfter($field);
  }

  // Removes every error message and red border in the form
  function clearErrors() {
    $form.find('.error-message').remove();
    $form.find('.invalid').removeClass('invalid');
  }
});
