/* global jQuery3 */
/**
 * Scroll to the warning.
 */
jQuery3.fn.scrollView = function () {
  return this.each(function () {
    jQuery3('html, body').animate({
      scrollTop: jQuery3(this).offset().top - (jQuery3(window).height() / 2)
    }, 1000);
  });
};
jQuery3(document).ready(function () {
  jQuery3('.highlight').scrollView();
});
jQuery3('.analysis-collapse-button').click(function () {
  jQuery3('#analysis-description').collapse('toggle');
  jQuery3('.analysis-collapse-button').toggleClass('open');
});
